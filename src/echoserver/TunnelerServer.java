package echoserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;

public class TunnelerServer {
    private static final int PORT = 8888;
    
    private static HashMap<String, Tank> tanks = new HashMap();
    
    private static HashSet<Handler> connections = new HashSet();

    public static void main(String[] args) throws Exception {
        System.out.println("The tunneler server is running on port "+PORT+".");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                Handler h = new Handler(listener.accept());
                h.start();
                connections.add(h);
            }
        } finally {
            listener.close();
        }
    }
    
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        public PrintWriter out;
        private long pushTimer = System.currentTimeMillis();
        private long frameTimer = System.currentTimeMillis();

        public Handler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (tanks.keySet()) {
                        if (!tanks.keySet().contains(name)) {
                            tanks.put(name, null);
                            break;
                        }
                    }
                }
                out.println("NAMEACCEPTED");
                System.out.println("Connection with " + name + " established.");
                
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        while(true)
                        {
                            /*if(System.currentTimeMillis()-pushTimer > 100)
                            {
                                pushTimer = System.currentTimeMillis();
                                for(Tank t : tanks.values())
                                {
                                    if(t != null && !t.getName().equals(name))
                                    {
                                        //t.update();
                                    }
                                }
                            }*/
                            while(System.currentTimeMillis()-frameTimer >= 16)
                            {
                                frameTimer += 16;
                                for(Tank t : tanks.values())
                                {
                                    if(t != null)
                                    {
                                        t.update();
                                        out.println(t.getName() + " " + t.getX() + " " + t.getY() + " " + t.getXSpeed() + " " + t.getYSpeed() + " " + t.getDirection());
                                    }
                                }
                            }
                        }
                    }
                }.start();
                
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    String[] splitty = input.split(" ");
                    if(splitty[0].equals("PUSHTANK"))
                    {
                        Tank t = new Tank(name, Float.parseFloat(splitty[1]), Float.parseFloat(splitty[2]));
                        tanks.put(name, t);
                    }
                    else if(splitty[0].equals("KEYSTROKE"))
                    {
                        Tank t = tanks.get(name);
                        if(splitty[1].equals("RIGHT") || splitty[1].equals("LEFTRELEASED"))
                        {
                            t.setXSpeed(t.getXSpeed()+3);
                        }
                        else if(splitty[1].equals("LEFT") || splitty[1].equals("RIGHTRELEASED"))
                        {
                            t.setXSpeed(t.getXSpeed()-3);
                        }
                        else if(splitty[1].equals("UP") || splitty[1].equals("DOWNRELEASED"))
                        {
                            t.setYSpeed(t.getYSpeed()+3);
                        }
                        else if(splitty[1].equals("DOWN") || splitty[1].equals("UPRELEASED"))
                        {
                            t.setYSpeed(t.getYSpeed()-3);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                if (name != null) {
                    tanks.remove(name);
                    for(Handler h : connections)
                    {
                        h.out.println("DISCONNECT " + name);
                    }
                }
                try {
                    socket.close();
                    System.out.println("Connection with " + name + " has been terminated");
                } catch (IOException e) {
                }
            }
        }
    }
}