package echoserver;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TunnelerServer {
    private static final int PORT = 8888;
    public static final int FRAME_RATE = 50;
    
    private static int phase = 0; //0 = tunneling, 1 = shooting
    
    private static long pushTimer = System.currentTimeMillis();
    private static long frameTimer = System.currentTimeMillis();
    
    private static HashMap<String, Tank> tanks = new HashMap();
    
    private static HashSet<Handler> connections = new HashSet();
    private static HashSet<Bullet> bullets = new HashSet();
    
    private static int[][] grid = new int[2000][2000];

    public static void main(String[] args) throws Exception {
        for(int i = 0; i < grid.length; i++)
            for(int j = 0; j < grid[i].length; j++)
                if(i < 400 || i > grid.length-400 || j < 400 || j > grid[i].length-400)
                    grid[i][j] = 2;
                else
                    grid[i][j] = 1;
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
                    long timeMillis = System.currentTimeMillis();
                    if(timeMillis-frameTimer > 1000/FRAME_RATE)
                    {
                        for(Tank t : tanks.values())
                        {
                            if(t != null)
                            {
                                try
                                {
                                    double[] dat = t.mockUpdate(timeMillis-frameTimer);
                                    double angle = dat[2];
                                    double corner1x = dat[0]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    double corner1y = dat[1]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    //double corner2x = dat[0]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner2y = dat[1]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    //double corner3x = dat[0]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner3y = dat[1]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    //double corner4x = dat[0]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner4y = dat[1]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    int radius = (int)(Tank.imgScale*Tank.tankImg.getWidth(null)/2+3);
                                    double incAdjustment = 1;
                                    double outXInc = Math.cos(angle)*incAdjustment, outYInc = Math.sin(angle)*incAdjustment, inXInc = Math.sin(angle)*incAdjustment, inYInc=-Math.cos(angle)*incAdjustment;
                                    double widthCounter = 0;
                                    boolean canMove = true;
                                    double numUnits = 0; //efficiency counter. Total # of sends to all clients/pixel processing count.
                                    ArrayList<Point> bucket = new ArrayList();
                                    if(phase == 0) //digging a circle around the tank. println all chords in the circle of 1 pixel width.
                                        for(int startX = (int)(dat[0]-radius); startX < (int)(dat[0]+radius+1); startX++)
                                        {
                                            double dist = Math.sqrt(radius*radius-(dat[0]-startX)*(dat[0]-startX));
                                            for(int startY = (int)(dat[1]-dist); startY < (int)(dat[1]+dist); startY++)
                                            {
                                                if(grid[startX][startY] == 1)
                                                {
                                                    bucket.add(new Point(startX, startY));
                                                }
                                                else if(grid[startX][startY] == 2)
                                                {
                                                    //System.out.println("Current: " + t.getX() + "," + t.getY() + "\nNext: " + dat[0] + "," + dat[1] + "\nCollided at: " + startX + "," + startY);
                                                    canMove = false;
                                                    bucket.clear();
                                                    break;
                                                }
                                            }
                                        }
                                    else //can't dig, just moving
                                        while(widthCounter < Tank.imgScale*Tank.tankImg.getWidth(null)/incAdjustment+1)
                                        {
                                            int heightCounter = 0;
                                            double qx = corner1x, qy = corner1y;
                                            while(heightCounter < Tank.imgScale*Tank.tankImg.getHeight(null)/incAdjustment+1)
                                            {
                                                if(grid[(int)Math.round(qx)][(int)Math.round(qy)] != 0)
                                                {
                                                    canMove = false;
                                                    break;
                                                }
                                                numUnits++;
                                                qx += inXInc;
                                                qy += inYInc;
                                                heightCounter++;
                                            }
                                            corner1x += outXInc;
                                            corner1y += outYInc;
                                            widthCounter++;
                                        }
                                    if(canMove)
                                    {
                                        for(Point p : bucket)
                                        {
                                            grid[(int)p.getX()][(int)p.getY()] = 0;
                                            for(Handler h : connections)
                                            {
                                                h.out.println("GRID/" + (int)p.getX() + "/" + (int)p.getY() + "/" + grid[(int)p.getX()][(int)p.getY()]);
                                                numUnits++;
                                            }
                                        }
                                        t.update(timeMillis-frameTimer);
                                    }
                                    //System.out.println(numUnits);
                                } catch(Exception exc) {
                                    exc.printStackTrace();
                                }
                                
                            }
                        }
                        frameTimer = System.currentTimeMillis();
                    }
                }
            }
        }.start();
        System.out.println("The tunneler server is running on port "+PORT+".");
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                Handler h = new Handler(listener.accept());
                h.start();
                connections.add(h);
            }
        }
    }
    
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        public PrintWriter out;
        private Thread myThread;
        private long myFrameTimer = System.currentTimeMillis();

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
                    if (name == null || name.equals("") || name.equals("null") || name.split("/").length != 1) {
                    } else
                    synchronized (tanks.keySet()) {
                        if (!tanks.keySet().contains(name)) {
                            tanks.put(name, null);
                            break;
                        }
                    }
                }
                out.println("NAMEACCEPTED/" + name);
                System.out.println("Connection with " + name + " established.");
                
                for(int i = 0; i < grid.length; i++)
                    for(int j = 0; j < grid[i].length; j++)
                        out.println("GRID/" + i + "/" + j + "/" + grid[i][j]);
                
                myThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        while(true)
                        {
                            long timeMillis = System.currentTimeMillis();
                            if(timeMillis-myFrameTimer > 1000/FRAME_RATE)
                            {
                                for(Tank t : tanks.values())
                                {
                                    if(t != null)
                                    {
                                        out.println("PUSHTANK/" + t.getName() + "/" + t.getX() + "/" + t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + Tank.imgScale);
                                    }
                                }
                                myFrameTimer = System.currentTimeMillis();
                            }
                        }
                    }
                };
                myThread.start();
                
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    String[] splitty = input.split("/");
                    if(splitty[0].equals("PUSHTANK"))
                    {
                        Tank t = new Tank(name, Float.parseFloat(splitty[1]), Float.parseFloat(splitty[2]));
                        tanks.put(name, t);
                    }
                    else if(splitty[0].equals("KEYSTROKE"))
                    {
                        Tank t = tanks.get(name);
                        if(t.getYSpeed() != 0 && t.getXSpeed() != 0)
                        {
                            t.setXSpeed(t.getXSpeed()*Math.sqrt(2));
                            t.setYSpeed(t.getYSpeed()*Math.sqrt(2));
                        }
                        switch (splitty[1]) {
                            case "RIGHT":
                            case "LEFTRELEASED":
                                t.setXSpeed(t.getXSpeed()+Tank.TANK_SPEED);
                                //t.setRotationSpeed(t.getRotationSpeed()-Tank.TANK_ROTATION_SPEED);
                                break;
                            case "LEFT":
                            case "RIGHTRELEASED":
                                t.setXSpeed(t.getXSpeed()-Tank.TANK_SPEED);
                                //t.setRotationSpeed(t.getRotationSpeed()+Tank.TANK_ROTATION_SPEED);
                                break;
                            case "UP":
                            case "DOWNRELEASED":
                                t.setYSpeed(t.getYSpeed()+Tank.TANK_SPEED);
                                //t.setSpeed(t.getSpeed()+Tank.TANK_SPEED);
                                break;
                            case "DOWN":
                            case "UPRELEASED":
                                t.setYSpeed(t.getYSpeed()-Tank.TANK_SPEED);
                                //t.setSpeed(t.getSpeed()-Tank.TANK_SPEED);
                                break;
                            case "SPACE":
                                
                                break;
                            case "Q":
                                if(phase == 0){
                                    phase = 1; Tank.imgScale = 3;
                                }else{
                                    phase = 0; Tank.imgScale = 6;
                                }
                                for(Handler h : connections)
                                {
                                    h.out.println("PHASE/" + phase);
                                }
                                break;
                        }
                        if(t.getYSpeed() != 0 && t.getXSpeed() != 0)
                        {
                            t.setXSpeed(t.getXSpeed()/Math.sqrt(2));
                            t.setYSpeed(t.getYSpeed()/Math.sqrt(2));
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
                        h.out.println("DISCONNECT/" + name);
                    }
                    myThread.interrupt();
                    try {
                        myThread.join();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
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