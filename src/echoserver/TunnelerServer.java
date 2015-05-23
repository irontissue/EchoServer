package echoserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class TunnelerServer {
    private static final int PORT = 8888;
    
    private static HashMap<String, Tank> tanks = new HashMap();

    public static void main(String[] args) throws Exception {
        System.out.println("The tunneler server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                for(Tank t : tanks.values())
                {
                    if(t != null)
                        t.update();
                }
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }
    
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private long pushTimer = System.currentTimeMillis();

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
                System.out.println("Connection with " + name + " established.");
                
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        while(true)
                        {
                            if(System.currentTimeMillis()-pushTimer > 1000)
                            {
                                pushTimer = System.currentTimeMillis();
                                for(Tank t : tanks.values())
                                {
                                    if(t != null && !t.getName().equals(name))
                                    {
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
                    if(splitty[0].equalsIgnoreCase("pushtank"))
                    {
                        System.out.println("tank recieved!!11!!1!");
                        Tank t = new Tank(name, Float.parseFloat(splitty[1]), Float.parseFloat(splitty[2]));
                        t.setXSpeed(Float.parseFloat(splitty[3]));
                        t.setYSpeed(Float.parseFloat(splitty[4]));
                        t.setDirection(Integer.parseInt(splitty[5]));
                        tanks.put(name, t);
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                if (name != null) {
                    tanks.remove(name);
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