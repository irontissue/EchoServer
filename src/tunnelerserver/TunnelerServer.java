package tunnelerserver;

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
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TunnelerServer {
    private static final int PORT = 8888;
    public static final int FRAME_RATE = 50;

    public static void main(String[] args) throws Exception
    {
        System.out.println("The tunneler server is running on port "+PORT+".");
        int numRooms = 0;
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                Room r = new Room(2);
                numRooms++;
                while(r.handlers.size() < r.roomSize-1)
                {
                    r.addPotentialPlayer(new Handler(listener.accept()));
                }
                System.out.println("STARTING A " + r.roomSize + " PLAYER GAME");
                r.startGame();
            }
        }
    }
    
    private static class Room extends Thread{
        private int phase = 0; //0 = tunneling, 1 = shooting
        private int roomSize;
        private int numTeams = 2;
        private int teamSetter = 0;
        private int[] numDeadOnTeams;
        
        private final int GAME_LENGTH = 120; //seconds
        
        private boolean gameEnded = false;
        
        private long pushTimer;
        private long frameTimer;
        private long gameStartTime; //seconds

        public HashMap<String, Tank> tanks = new HashMap();
        public HashMap<String, Handler> handlers = new HashMap();
        
        private ArrayList<Bullet> bullets = new ArrayList();

        private int[][] grid = new int[2000][2000]; //values in grid represent tile type. 0 is nothing, 1 is diggable dirt, 2 is wall.
                                                    //If value is above 2, there is a bullet there with damage = value/100.
                                                    //Essentially, a grid value 100 or above means theres damage there.
        
        public Room(int size)
        {
            roomSize = size;
            numDeadOnTeams = new int[numTeams];
            for(int i = 0; i < grid.length; i++)
                for(int j = 0; j < grid[i].length; j++)
                    if(i < 200 || i > grid.length-200 || j < 200 || j > grid[i].length-200)
                        grid[i][j] = 2;
                    else
                        grid[i][j] = 1;
        }
        
        public boolean isFull()
        {
            return (handlers.size() == roomSize);
        }
        
        public boolean checkAvailability(Point p)
        {
            for(Tank t : tanks.values())
            {
                if(t != null && Math.sqrt(Math.pow(t.getX()-p.x,2) + Math.pow(t.getY()-p.y,2)) < 400)
                {
                    return false;
                }
            }
            return true;
        }
        
        public void startGame()
        {
            while(handlers.size() != roomSize || tanks.size() != roomSize){
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            start();
        }
        
        public void addPotentialPlayer(Handler h)
        {
            h.setRoom(this);
            h.setTeam(teamSetter);
            teamSetter++;
            if(teamSetter > numTeams-1)
                teamSetter = 0;
            h.start();
        }
        
        public void addPlayer(Handler h, Tank t)
        {
            handlers.put(t.getName(), h);
            tanks.put(t.getName(), t);
            String msg = " Waiting for " + (roomSize-handlers.size()) + " more players...";
            if(isFull())
                msg = "";
            for(Handler hh : handlers.values())
            {
                hh.out.println("SYSTEMMESSAGE/" + t.getName() + " has joined the game." + msg);
            }
            for(int i = (int)(t.getX()-150); i < (int)(t.getX()+150); i++)
            {
                for(int j = (int)(t.getY()-150); j < (int)(t.getY()+150); j++)
                {
                    if(!h.discoveredGrid[i][j])
                    {
                        h.out.println("GRID/" + i + "/" + j + "/" + grid[i][j]);
                        h.discoveredGrid[i][j] = true;
                    }
                }
            }
        }
        
        public void changePhase()
        {
            if(phase == 0){
                phase = 1; Tank.imgScale = 3;
            }else{
                phase = 0; Tank.imgScale = 6;
            }
            for(Handler h : handlers.values())
            {
                h.out.println("PHASE/" + phase);
            }
        }
        
        @Override
        public void run()
        {
            try
            {
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/Game starts in 5");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/4");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/3");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/2");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/1");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/GO!");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            gameStartTime = System.currentTimeMillis()/1000;
            frameTimer = System.currentTimeMillis()-1000/FRAME_RATE-1;
            while(!gameEnded)
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
                if(timeMillis/1000-gameStartTime >= GAME_LENGTH)
                {
                    gameEnded = true;
                    HashSet<Integer> winningTeams = new HashSet();
                    for(int j = 0; j < numDeadOnTeams.length; j++)
                    {
                        if(numDeadOnTeams[j] < roomSize/2)
                        {
                            winningTeams.add(j);
                        }
                    }
                    String msg = "FAILED CALC";
                    if(winningTeams.size() > 1)
                    {
                        msg = "There is a tie between teams ";
                        for(int team : winningTeams)
                            msg += team + ",";
                        msg = msg.substring(0, msg.length()-1);
                        msg += ".";
                    }
                    else
                    {
                        for(int team : winningTeams)
                            msg = "Team " + team + " has won.";
                    }
                    System.out.println(msg);
                    synchronized(handlers.values()){
                        for(Handler h : handlers.values())
                        {
                            h.out.println("SYSTEMMESSAGE/The game is over.");
                            h.out.println("SYSTEMMESSAGE/"+msg);
                        }
                    }
                    interrupt();
                }
                else if(timeMillis-frameTimer > 1000/FRAME_RATE)
                {
                    if(tanks.isEmpty() || handlers.isEmpty()){
                        gameEnded = true;
                        interrupt();
                    }
                    Stack<Point> bucket = new Stack();
                    HashSet<Tank> deadTanks = new HashSet();
                    synchronized(bullets)
                    {
                        int sz = bullets.size();
                        for(int u = 0; u < sz; u++)
                        {
                            if(u > bullets.size()-1)
                                break;
                            else
                            {
                                Bullet b = bullets.get(u);
                                grid[(int)b.getX()][(int)b.getY()] -= b.getDamage()*100;
                                double[] dat = b.mockUpdate(timeMillis-frameTimer);
                                int gridType = grid[(int)dat[0]][(int)dat[1]]%100;
                                /*
                                2 cases for bullet removal tested here. The third one is in the tanks loop below.
                                The three cases are 1) bullet hits wall and it doesn't pierce
                                                    2) bullet's lifetime is over
                                                    3) bullet hits tank and it doesn't pierce
                                Cases 1 and 2 are tested here.
                                */
                                if(gridType != 0 && !b.piercesWalls)
                                {
                                    bullets.remove(b);
                                    u--;
                                    if(b.getName().split(" ").length > 1)
                                    {
                                        handlers.get(b.getName().split(" ", 2)[1]).numBullets-=1;
                                    }
                                    for(int i = 0; i < 4; i++)
                                    {
                                        double randSpd = Math.random()*2*TunnelerServer.FRAME_RATE/1000.0;
                                        double randRotate = Math.random()*Math.PI/2+(b.getRotation()-Math.PI-Math.PI/4);
                                        bullets.add(new Bullet("bullet2", dat[0], dat[1], randSpd, randRotate, 300, 5, true, true));
                                        grid[(int)dat[0]][(int)dat[1]] += b.getDamage()*20; //ricochet bullets do 1/5 dmg
                                        synchronized(handlers.values()){
                                            for(Handler h : handlers.values()){
                                                h.out.println("BULLET/bullet2/" + dat[0] + "/" + dat[1] + "/" + randSpd + "/" + randRotate + "/" + 300 + "/" + 5 + "/true/true");
                                            }
                                        }
                                    }
                                }
                                else if(dat[2] >= b.getLifetime())
                                {
                                    bullets.remove(b);
                                    u--;
                                    if(b.getName().split(" ").length > 1)
                                    {
                                        handlers.get(b.getName().split(" ", 2)[1]).numBullets-=1;
                                    }
                                }
                                else
                                {
                                    b.update(timeMillis-frameTimer);
                                    grid[(int)dat[0]][(int)dat[1]] += b.getDamage()*100;
                                }
                            }
                        }
                    }
                    synchronized(handlers.values()){
                        for(String s : handlers.keySet())
                        {
                            Handler h = handlers.get(s);
                            Tank t = tanks.get(s);
                            if(t != null && !t.isDead())
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
                                    double incAdjustment = 1.0;
                                    double outXInc = Math.cos(angle)*incAdjustment, outYInc = Math.sin(angle)*incAdjustment, inXInc = Math.sin(angle)*incAdjustment, inYInc=-Math.cos(angle)*incAdjustment;
                                    double widthCounter = 0;
                                    boolean canMove = true;
                                    double numUnits = 0; //efficiency counter. Total # of sends to all clients/pixel processing count.
                                    if(phase == 0) //digging a circle around the tank. println all chords in the circle of 1 pixel width.
                                    {
                                        Stack<Point> tempBucket = new Stack();
                                        for(int startX = (int)(dat[0]-radius); startX < (int)(dat[0]+radius+1); startX++)
                                        {
                                            double dist = Math.sqrt(radius*radius-(dat[0]-startX)*(dat[0]-startX));
                                            for(int startY = (int)(dat[1]-dist); startY < (int)(dat[1]+dist); startY++)
                                            {
                                                int type = grid[startX][startY]%100;
                                                if(type == 1)
                                                {
                                                    tempBucket.push(new Point(startX, startY));
                                                    numUnits++;
                                                }
                                                else if(type == 2)
                                                {
                                                    canMove = false;
                                                    tempBucket.clear();
                                                    break;
                                                }
                                            }
                                        }
                                        bucket.addAll(tempBucket);
                                    }
                                    else //can't dig, just moving
                                    {
                                        while(widthCounter < Tank.imgScale*Tank.tankImg.getWidth(null)/incAdjustment+1)
                                        {
                                            int heightCounter = 0;
                                            double qx = corner1x, qy = corner1y;
                                            while(heightCounter < Tank.imgScale*Tank.tankImg.getHeight(null)/incAdjustment+1)
                                            {
                                                //Third case for bullet removal test, see bullet loop above for explanation
                                                int dmg = grid[(int)Math.round(qx)][(int)Math.round(qy)]/100;
                                                int gridType = grid[(int)Math.round(qx)][(int)Math.round(qy)]-dmg*100;
                                                if(dmg > 0)
                                                {
                                                    grid[(int)Math.round(qx)][(int)Math.round(qy)] %= 100;
                                                    for(Bullet b : bullets)
                                                    {
                                                        if(!b.piercesTanks && b.getX() == (int)Math.round(qx) && b.getY() == (int)Math.round(qy))
                                                        {
                                                            bullets.remove(b);
                                                            break;
                                                        }
                                                    }
                                                    t.setHealth(t.getHealth()-dmg);
                                                    if(gridType != 0 || t.getHealth() <= 0)
                                                    {
                                                        canMove = false;
                                                        if(t.getHealth() <= 0)
                                                        {
                                                            deadTanks.add(t);
                                                            t.setDead(true);
                                                        }
                                                        break;
                                                    }
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
                                    }
                                    //System.out.println(numUnits); //Efficiency of above collision algorithms
                                    if(canMove)
                                    {
                                        for(int i = (int)(dat[0]-150); i < (int)(dat[0]+150); i++)
                                        {
                                            for(int j = (int)(dat[1]-150); j < (int)(dat[1]+150); j++)
                                            {
                                                if(!h.discoveredGrid[i][j])
                                                {
                                                    h.out.println("GRID/" + i + "/" + j + "/" + grid[i][j]);
                                                    h.discoveredGrid[i][j] = true;
                                                }
                                            }
                                        }
                                        t.update(timeMillis-frameTimer);
                                    }
                                } catch(Exception exc) {
                                    exc.printStackTrace();
                                }

                            }
                        }
                    }
                    for(Point p : bucket)
                    {
                        grid[(int)p.getX()][(int)p.getY()] /= 100;
                        grid[(int)p.getX()][(int)p.getY()] *= 100;
                        int type = grid[(int)p.getX()][(int)p.getY()]%100;
                        for(Handler h : handlers.values())
                        {
                            h.out.println("GRID/" + (int)p.getX() + "/" + (int)p.getY() + "/" + type);
                        }
                    }
                    for(Tank t : deadTanks)
                    {
                        numDeadOnTeams[t.getTeam()] += 1;
                        for(Handler h : handlers.values())
                        {
                            h.out.println("SYSTEMMESSAGE/" + t.getName() + " has died.");
                        }
                    }
                    for(int num : numDeadOnTeams)
                        if(num == roomSize/2)
                        {
                            gameStartTime = timeMillis/1000-120;
                        }
                    frameTimer = System.currentTimeMillis();
                    //System.out.println(Thread.activeCount());
                }
            }
        }
    }
    
    private static class Handler extends Thread {
        public String name;
        public int team;
        private int numBullets=0;
        private Socket socket;
        private BufferedReader in;
        public PrintWriter out;
        private Room myRoom;
        public boolean[][] discoveredGrid = new boolean[2000][2000];
        private long myFrameTimer = System.currentTimeMillis();

        public Handler(Socket socket) {
            this.socket = socket;
            try
            {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        public void setTeam(int t)
        {
            team = t;
        }
        
        public void setRoom(Room r)
        {
            myRoom = r;
        }
        
        @Override
        public void run() {
            try {
                boolean nameSet = false;
                while (!nameSet) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    boolean allNull = true;
                    int randomX = (int)(Math.random()*(myRoom.grid.length-600))+300;
                    int randomY = (int)(Math.random()*(myRoom.grid[0].length-600))+300;
                    while(myRoom.checkAvailability(new Point(randomX, randomY)) == false)
                    {
                        randomX = (int)(Math.random()*(myRoom.grid.length-600))+300;
                        randomY = (int)(Math.random()*(myRoom.grid[0].length-600))+300;
                    }
                    if (name == null || name.equals("") || name.equals("null") || name.split("/").length != 1) {
                    } else
                        for(Tank t : myRoom.tanks.values())
                        {
                            if(t != null && !t.getName().equals(name))
                            {
                                Tank myT = new Tank(name, randomX, randomY);
                                myT.setTeam(team);
                                myRoom.addPlayer(this, myT);
                                nameSet = true;
                                allNull = false;
                                break;
                            }
                            else if(t != null)
                            {
                                allNull = false;
                            }
                        }
                    if(allNull)
                    {
                        myRoom.addPlayer(this, new Tank(name, randomX, randomY));
                        nameSet = true;
                    }
                }
                out.println("NAMEACCEPTED/" + name);
                out.println("MESSAGE/You are on team " + team + ".");
                System.out.println("Connection with " + name + " established.");
                
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        while(!myRoom.gameEnded)
                        {
                            long timeMillis = System.currentTimeMillis();
                            if(timeMillis-myFrameTimer > 1000/FRAME_RATE)
                            {
                                synchronized(myRoom.tanks.values()){
                                    for(Tank t : myRoom.tanks.values())
                                        if(t != null)
                                            out.println("PUSHTANK/" + t.getName() + "/" + t.getX() + "/" + t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + team + "/" + Tank.imgScale + "/" + t.getHealth());
                                }
                                myFrameTimer = System.currentTimeMillis();
                            }
                        }
                    }
                }.start();
                
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }
                    Tank t = myRoom.tanks.get(name);
                    if(!t.isDead())
                    {
                        String[] splitty = input.split("/");
                        /*if(splitty[0].equals("PUSHTANK"))
                        {
                            Tank t = new Tank(name, Float.parseFloat(splitty[1]), Float.parseFloat(splitty[2]));
                            tanks.put(name, t);
                            for(int i = (int)(t.getX()-175); i < (int)(t.getX()+175); i++)
                                for(int j = (int)(t.getY()-175); j < (int)(t.getY()+175); j++)
                                {
                                    out.println("GRID/" + i + "/" + j + "/" + grid[i][j]);
                                    discoveredGrid[i][j] = true;
                                }
                        }
                        else */if(splitty[0].equals("KEYSTROKE"))
                        {
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
                                case "SHOOT":
                                    if(myRoom.phase == 1 && numBullets < 5)
                                    {
                                        myRoom.bullets.add(new Bullet("bullet1 "+name, t.getX()+Math.cos(t.getRotation())*15, t.getY()+Math.sin(t.getRotation())*15, Bullet.STANDARD_BULLET_SPEED, t.getRotation(), Bullet.STANDARD_BULLET_LIFETIME, 10, false, false));
                                        numBullets += 1;
                                        myRoom.grid[(int)(t.getX()+Math.cos(t.getRotation())*15)][(int)(t.getY()+Math.sin(t.getRotation())*15)] += 1000;
                                        synchronized(myRoom.handlers.values()){
                                            for(Handler h : myRoom.handlers.values()){
                                                h.out.println("BULLET/bullet1 " +name+ "/" + (t.getX()+Math.cos(t.getRotation())*15) + "/" + (t.getY()+Math.sin(t.getRotation())*15) + "/" + Bullet.STANDARD_BULLET_SPEED + "/" + t.getRotation() + "/" + Bullet.STANDARD_BULLET_LIFETIME + "/" + 10 + "/false/false");
                                            }
                                        }
                                    }
                                    break;
                                case "Q":
                                    myRoom.changePhase();
                                    break;
                            }
                            if(t.getYSpeed() != 0 && t.getXSpeed() != 0)
                            {
                                t.setXSpeed(t.getXSpeed()/Math.sqrt(2));
                                t.setYSpeed(t.getYSpeed()/Math.sqrt(2));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                if (name != null) {
                    myRoom.handlers.remove(name);
                    myRoom.tanks.remove(name);
                    for(Handler h : myRoom.handlers.values())
                    {
                        h.out.println("DISCONNECT/" + name);
                    }
                    Thread.currentThread().interrupt();
                    try {
                        socket.close();
                        System.out.println("Connection with " + name + " has been terminated");
                    } catch (IOException e) {
                    }
                    return;
                }
            }
        }
    }
}