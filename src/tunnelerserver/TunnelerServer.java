package tunnelerserver;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import javax.imageio.ImageIO;

public class TunnelerServer {
    private final int PORT;
    private Thread listenerThread;
    public final int FRAME_RATE = 30;
    public boolean closed = false;
    private String[] keywords = {"LOGIN", "CREATE", "SUBMITNAME", "NAMEACCEPTED", "DISCONNECT", "GRID", "PHASE", "BULLET", "SYSTEMMESSAGE", "MESSAGE", "PUSHTANK", "RECONNECT", "green", "blue", "Green", "Blue", "Admin"};
    private HashSet<Handler> connections = new HashSet<>();
    private Room curr2PRoom, curr6PRoom, curr10PRoom;
    private boolean[][] digMask = new boolean[48][48];
    
    public TunnelerServer(int port)
    {
        PORT = port;
        
        curr2PRoom = new Room(2);
        curr6PRoom = new Room(6);
        curr10PRoom = new Room(10);
        
        try {
            BufferedImage b = ImageIO.read(new File("resources/dig2.png"));
            for(int i = 0; i < b.getWidth(); i++)
            {
                for(int j = 0; j < b.getHeight(); j++)
                {
                    int rgb = b.getRGB(i, j);
                    if(rgb != -1)
                    {
                        digMask[i][j] = true;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        /*new Thread(){
            @Override
            public void run()
            {
                while(true)
                {
                    try {
                        Thread.sleep(2000);
                        System.out.println(Thread.activeCount());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }.start();*/ //Thread leakage test
        
        System.out.println("The tunneler server is running on port "+PORT+".");
        listenerThread = new Thread() {
            @Override
            public void run()
            {
                try (ServerSocket listener = new ServerSocket(PORT)) {
                    while (true) {
                        Handler h = new Handler(listener.accept());
                        h.start();
                        connections.add(h);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        };
        listenerThread.start();
        
        new Thread() {
            boolean stop = false;
            @Override
            public void run()
            {
                while(!stop)
                {
                    try {
                        Thread.sleep(1000);
                        if(closed && getSize() == 0)
                        {
                            listenerThread.interrupt();
                            stop = true;
                            System.out.println("Tunneler server on port " + PORT + " has been closed.");
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }.start();
    }
    
    public int getPort()
    {
        return PORT;
    }
    
    public int getSize()
    {
        return connections.size();
    }
    
    public void addToRoom(Handler h, Tank t, int roomID)
    {
        if(roomID == 0)
        {
            curr2PRoom.addPlayer(h, t);
            if(curr2PRoom.isFull())
            {
                curr2PRoom.startGame();
                curr2PRoom = new Room(2);
            }
        }
        else if(roomID == 1)
        {
            curr6PRoom.addPlayer(h, t);
            if(curr6PRoom.isFull())
            {
                curr6PRoom.startGame();
                curr6PRoom = new Room(6);
            }
        }
        else
        {
            curr10PRoom.addPlayer(h, t);
            if(curr10PRoom.isFull())
            {
                curr10PRoom.startGame();
                curr10PRoom = new Room(10);
            }
        }
    }
    
    private class Room extends Thread{
        private int phase = 0; //0 = tunneling, 1 = shooting
        private int roomSize;
        private int numTeams = 2;
        private int teamSetter = 0;
        private int[] numDeadOnTeams;
        
        private int gameLength; //seconds
        private int currBulID = 0;
        
        private boolean gameEnded = false;
        private boolean phaseChanging = false;
        
        private long pushTimer; //ms
        private long frameTimer; //ms
        private long gameStartTime; //seconds
        
        private float imgScale = 3;
        
        private HashSet<Point> write = new HashSet<>();
        private String writer = "";
        private int numWriter = 0;

        public HashMap<String, Tank> tanks = new HashMap<>();
        public HashMap<String, Handler> handlers = new HashMap<>(); //if only java default pkg had dictionaries like python!
        
        private ArrayList<Bullet> bullets = new ArrayList<>();

        private int[][] grid; //values in grid represent tile type. 0 is nothing, 1 is diggable dirt, 2 is wall.
                                                    //If value is above 2, there is a bullet there with damage = value/100.
                                                    //Essentially, a grid value 100 or above means theres damage there.
        
        public Room(int size)
        {
            roomSize = size;
            gameLength = 15*size/2+45;
            numDeadOnTeams = new int[numTeams];
            grid = new int[(int)(Math.pow(size,0.8)*919)+400][(int)(Math.pow(size,0.8)*804)+400]; //1v1: 800x700 per player. 5v5: 580x507 per player.
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
        
        public boolean checkAvailability(Point p) //ensures no tank is too close to another
        {
            for(Tank t : tanks.values())
            {
                if(t != null && Math.sqrt(Math.pow(t.getX()-p.x,2) + Math.pow(t.getY()-p.y,2)) < 450)
                {
                    return false;
                }
            }
            return true;
        }
        
        public void startGame() //waits until all players have picked names and are ready to go
        {
            /*while(handlers.size() != roomSize || tanks.size() != roomSize){
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }*/
            for(Handler h : handlers.values()) //update tanks to clients
                for(Tank t : tanks.values())
                {
                    h.out.println("PUSHTANK/" + t.getName() + "/" + (int)t.getX() + "/" + (int)t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + t.getTeam() + "/" + (int)t.getHealth());
                }
            start();
        }
        
        public void addPlayer(Handler h, Tank t) //called when a client joins this room
        {
            h.myRoom = this;
            h.team = teamSetter;
            t.setTeam(teamSetter);
            teamSetter++;
            if(teamSetter > numTeams-1)
                teamSetter = 0;
            int initX = (int)(Math.random()*(grid.length-600))+300;
            int initY = (int)(Math.random()*(grid[0].length-600))+300;
            while(checkAvailability(new Point(initX, initY)) == false)
            {
                initX = (int)(Math.random()*(grid.length-600))+300;
                initY = (int)(Math.random()*(grid[0].length-600))+300;
            }
            t.setX(initX);
            t.setY(initY);
            h.initX = initX;
            h.initY = initY;
            t.initX = initX;
            t.initY = initY;
            h.out.println("GAMESETTING/"+gameLength);
            handlers.put(h.tName, h);
            tanks.put(h.tName, t);
            String msg = " Waiting for " + (roomSize-handlers.size()) + " more players...";
            if(isFull())
                msg = "";
            String teamColor = "0,0,1";
            if(h.team == 1)
                teamColor = "0,1,0";
            for(Handler hh : handlers.values())
            {
                hh.out.println("SYSTEMMESSAGE/" + t.getName() + "/" + teamColor + "/ has joined the game." + msg + "/0.75,0.75,0.75");
            }
            for(int i = initX-90; i < initX+90; i++)
                for(int j = initY-100; j < initY+100; j++)
                    if(i < initX-85 || i > initX+85 || j < initY-95 | j > initY+95)
                        if(i > initX-30 && i < initX+30 && (j < initY-95 || j > initY+95))
                            grid[i][j] = 0;
                        else
                            grid[i][j] = 2;
                    else
                        grid[i][j] = 0;
            /*h.discoveredGrid = new boolean[grid.length][grid[0].length];
            for(int i = 0; i < h.discoveredGrid.length; i++)
                for(int j = 0; j < h.discoveredGrid[i].length; j++)
                    if(i < 200 || i > h.discoveredGrid.length-200 || j < 200 || j > h.discoveredGrid[i].length-200)
                        h.discoveredGrid[i][j] = true;
                    else if(i > (int)(t.getX()-301) && i < (int)(t.getX()+301) && j > (int)(t.getY()-301) && j < (int)(t.getY()+301))
                        h.discoveredGrid[i][j] = true;*/
        }
        
        public void changePhase()
        {
            phaseChanging = true;
            //if(phase == 0){
                phase = 1; imgScale = 1.5f;
;            //}else{ //for phase changing test
                //phase = 0; Tank.imgScale = 3;
            //}
            long timerr = System.currentTimeMillis();
            for(Handler h : handlers.values())
            {
                h.out.println("PHASE/" + phase);
                tanks.get(h.tName).setXSpeed(0);
                tanks.get(h.tName).setYSpeed(0);
                tanks.get(h.tName).setX(h.initX);
                tanks.get(h.tName).setY(h.initY);
                /*for(Point p : write)
                    h.out.println("GC/" + p.x + "/" + p.y + "/0");*/
                if(!writer.equals(""))
                    h.out.println(writer);
            }
            long sleepTime = System.currentTimeMillis()-timerr;
            try{
                Thread.sleep(3000-sleepTime); //3sec for client to do fade in/out
            } catch(Exception ex) {
                ex.printStackTrace();
            }
            gameStartTime = System.currentTimeMillis()/1000;
            for(Handler h : handlers.values())
            {
                h.out.println("GAMESETTING/"+gameLength);
            }
            phaseChanging = false;
        }
        
        @Override
        public void run()
        {
            gameStartTime = System.currentTimeMillis()/1000;
            try
            {
                Thread.sleep(1500); //Wait 1.5 sec before countdown in case stuff is still happening
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/Game starts in 5/0.75,0.75,0.75");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/4/0.75,0.75,0.75");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/3/0.75,0.75,0.75");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/2/0.75,0.75,0.75");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/1/0.75,0.75,0.75");
                }
                Thread.sleep(1000);
                for(Handler h : handlers.values())
                {
                    h.out.println("SYSTEMMESSAGE/GO!/0.75,0.75,0.75");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            
            gameStartTime = System.currentTimeMillis()/1000;
            frameTimer = System.currentTimeMillis()-1000/FRAME_RATE-1;
            pushTimer = System.currentTimeMillis();
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
                //long timeMillis = System.currentTimeMillis();
                if(System.currentTimeMillis()/1000-gameStartTime >= gameLength)
                {
                    if(phase == 0)
                    {
                        gameLength = 15*roomSize/2+105;
                        changePhase();
                    }
                    else
                    {
                        gameEnded = true;
                        HashSet<Integer> winningTeams = new HashSet<Integer>();
                        for(int j = 0; j < numDeadOnTeams.length; j++)
                        {
                            if(numDeadOnTeams[j] < roomSize/2)
                            {
                                winningTeams.add(j);
                            }
                        }
                        String msg = "SYSTEMMESSAGE/The game is over. /0.75,0.75,0.75/";
                        if(winningTeams.size() > 1)
                        {
                            msg += "There is a tie between ";
                            for(int team : winningTeams) {
                                if(team == 0) {
                                    msg += "/0.75,0.75,0.75/blue/0,0,1/ team and ";
                                }
                                else if(team == 1) {
                                    msg += "/0.75,0.75,0.75/green/0,1,0/ team and ";
                                }
                            }
                            msg = msg.substring(0, msg.length()-5);
                            msg += "./0.75,0.75,0.75";
                        }
                        else
                        {
                            for(int team : winningTeams) {
                                if(team == 1)
                                    msg += "Green/0,1,0/ team has won!/0.75,0.75,0.75";
                                else
                                    msg += "Blue/0,0,1/ team has won!/0.75,0.75,0.75";
                            }
                        }
                        synchronized(handlers.values()){
                            for(Handler h : handlers.values())
                            {
                                h.out.println(msg);
                            }
                        }
                    }
                    interrupt();
                }
                else if(System.currentTimeMillis()-frameTimer > 1000/FRAME_RATE)
                {
                    if(tanks.isEmpty() || handlers.isEmpty()){
                        gameEnded = true;
                        interrupt();
                    }
                    Stack<Point> bucket = new Stack<Point>();
                    HashSet<Tank> deadTanks = new HashSet<Tank>();
                    synchronized(bullets) //bullets loop
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
                                double[] dat = b.mockUpdate(System.currentTimeMillis()-frameTimer);
                                Integer[] gridTypes = new Integer[(int)Math.sqrt(Math.pow(b.getX()-dat[0],2)+Math.pow(b.getY()-dat[1],2))];
                                for(int i = 0; i < gridTypes.length; i++)
                                {
                                    gridTypes[i] = grid[(int)(b.getX()+Math.cos(b.getRotation())*i)][(int)(b.getY()+Math.sin(b.getRotation())*i)]%100;
                                }
                                /*
                                2 cases for bullet removal tested here. The third one is in the tanks loop below.
                                The three cases are 1) bullet hits wall and it doesn't pierce
                                                    2) bullet's lifetime is over
                                                    3) bullet hits tank and it doesn't pierce
                                Cases 1 and 2 are tested here.
                                */
                                /*int type = grid[(int)b.getX()][(int)b.getY()]%100; //these three lines make the bullet destroy tiles
                                if(type == 1)
                                    grid[(int)b.getX()][(int)b.getY()] -= type;*/
                                boolean go = false;
                                for(int ii : gridTypes)
                                {
                                    if(ii != 0) {
                                        go = true;
                                        break;
                                    }
                                }
                                if(go && !b.piercesWalls)
                                {
                                    bullets.remove(b);
                                    u--;
                                    if(b.getSource() != null)
                                    {
                                        handlers.get(b.getSource()).numBullets-=1;
                                    }
                                    while(grid[(int)b.getX()][(int)b.getY()] == 0)
                                        b.update(1/b.getSpeed());
                                    for(int i = 0; i < 4; i++)
                                    {
                                        double randSpd = Math.random()*0.1;
                                        double spreadAng = Math.PI*3/5;
                                        double randRotate = Math.random()*spreadAng+(b.getRotation()-Math.PI-spreadAng/2);
                                        bullets.add(new Bullet("bullet2", null, currBulID, b.getX(), b.getY(), randSpd, randRotate, 300, 5, true, false));
                                        grid[(int)dat[0]][(int)dat[1]] += b.getDamage()*50; //ricochet bullets do approximately 1/2 dmg. Any bullets < 10 damage will have ricochet that does 0 damage.
                                        synchronized(handlers.values()){
                                            for(Handler h : handlers.values()){
                                                h.out.println("BULLET/bullet2/null/" + currBulID + "/" + b.getX() + "/" + b.getY() + "/" + randSpd + "/" + randRotate + "/" + 300 + "/" + 5 + "/true/false");
                                            }
                                        }
                                        ++currBulID;
                                    }
                                }
                                else if(dat[2] >= b.getLifetime())
                                {
                                    bullets.remove(b);
                                    u--;
                                    if(b.getSource() != null)
                                    {
                                        handlers.get(b.getSource()).numBullets-=1;
                                    }
                                }
                                else
                                {
                                    b.update(System.currentTimeMillis()-frameTimer);
                                    grid[(int)dat[0]][(int)dat[1]] += b.getDamage()*100;
                                }
                            }
                        }
                    }
                    synchronized(handlers.values()){ //tanks loop
                        for(String s : handlers.keySet())
                        {
                            Handler h = handlers.get(s);
                            Tank t = tanks.get(s);
                            String toWriteB = "";
                            for(int i = 0; i < bullets.size(); i++)
                            {
                                Bullet b = bullets.get(i);
                                if(isVisible(t.getX(), t.getY(), b.getX(), b.getY()))
                                {
                                    if(t.setVisibilityB(b.getID(), true))
                                        toWriteB += "VISB/" + b.getID() + "/true";
                                }
                                else
                                {
                                    if(t.setVisibilityB(b.getID(), false))
                                        toWriteB += "VISB/" + b.getID() + "/false";
                                }
                            }
                            if(!toWriteB.equals(""))
                            {
                                h.out.println(toWriteB);
                            }
                            if(t != null && !t.isDead())
                            {
                                try
                                {
                                    double[] dat = t.mockUpdate(System.currentTimeMillis()-frameTimer);
                                    double angle = dat[2];
                                    double corner1x = dat[0]-Tank.tankImg.getWidth(null)*imgScale*Math.cos(angle)/2 - Tank.tankImg.getHeight(null)*imgScale*Math.sin(angle)/2;
                                    double corner1y = dat[1]-Tank.tankImg.getWidth(null)*imgScale*Math.sin(angle)/2 + Tank.tankImg.getHeight(null)*imgScale*Math.cos(angle)/2;
                                    //double corner2x = dat[0]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner2y = dat[1]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    //double corner3x = dat[0]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner3y = dat[1]+Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    //double corner4x = dat[0]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.cos(angle)/2 + Tank.tankImg.getHeight(null)*Tank.imgScale*Math.sin(angle)/2;
                                    //double corner4y = dat[1]-Tank.tankImg.getWidth(null)*Tank.imgScale*Math.sin(angle)/2 - Tank.tankImg.getHeight(null)*Tank.imgScale*Math.cos(angle)/2;
                                    int radius = 24;//(int)(imgScale*Tank.tankImg.getWidth(null)/2+3);
                                    double incAdjustment = 1;
                                    double outXInc = Math.cos(angle)*incAdjustment, outYInc = Math.sin(angle)*incAdjustment, inXInc = Math.sin(angle)*incAdjustment, inYInc=-Math.cos(angle)*incAdjustment;
                                    double widthCounter = 0;
                                    boolean canMove = true;
                                    double numUnits = 0; //efficiency counter. Total # of sends to all clients/pixel processing count.
                                    if(phase == 0) //digging a circle around the tank. println all chords in the circle of 1 pixel width.
                                    {
                                        if(t.getHealth() <= 0)
                                        {
                                            deadTanks.add(t);
                                            t.setDead(true);
                                            canMove = false;
                                        }
                                        else if(t.getHealth() > 10)
                                        {
                                            Stack<Point> tempBucket = new Stack<>();
                                            for(int startX = (int)(dat[0]-radius); startX < (int)(dat[0]+radius); startX++)
                                            {
                                                for(int startY = (int)(dat[1]-radius); startY < (int)(dat[1]+radius); startY++)
                                                {
                                                    if(digMask[(int)(startX+radius-dat[0])][(int)(startY+radius-dat[1])])
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
                                                            startX = (int)(dat[0]+radius+1);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            if(canMove && tempBucket.size() > 25) { //higher num means less precise map to be sent to clients
                                                //write.add(new Point((int)dat[0], (int)dat[1]));
                                                writer += "GC/" + (int)dat[0] + "/" + (int)dat[1];
                                                /*for(Handler hh : handlers.values())
                                                    hh.out.println("GCUNIT/" + (int)dat[0] + "/" + (int)dat[1]);*/
                                                numWriter += 1;
                                                t.setHealth(t.getHealth()-(System.currentTimeMillis()-frameTimer)*0.007);
                                            }
                                            bucket.addAll(tempBucket);
                                        }
                                    }
                                    else //can't dig, just moving
                                    {
                                        while(widthCounter < imgScale*Tank.tankImg.getWidth(null)/incAdjustment+1)
                                        {
                                            int heightCounter = 0;
                                            double qx = corner1x, qy = corner1y;
                                            while(heightCounter < imgScale*Tank.tankImg.getHeight(null)/incAdjustment+1)
                                            {
                                                //Third case for bullet removal test, see bullet loop above for explanation
                                                int dmg = grid[(int)Math.round(qx)][(int)Math.round(qy)]/100;
                                                int gridType = grid[(int)Math.round(qx)][(int)Math.round(qy)]-dmg*100;
                                                if(dmg > 0)
                                                {
                                                    grid[(int)Math.round(qx)][(int)Math.round(qy)] %= 100;
                                                    for(int i = 0; i < bullets.size(); i++)
                                                    {
                                                        Bullet b = bullets.get(i);
                                                        if(!b.piercesTanks && Math.sqrt(Math.pow(b.getX()-qx, 2) + Math.pow(b.getY()-qy, 2)) < 2) //my algorithm is screwy so i'm lazy and just checked if bullet distance from check point is 2 pix away, if so then damages
                                                        {
                                                            bullets.remove(b);
                                                            i--;
                                                            if(b.getSource() != null)
                                                                handlers.get(b.getSource()).numBullets-=1;
                                                            break;
                                                        }
                                                    }
                                                    System.out.println(t.getName() + " damaged for " + dmg + " hp!");
                                                    t.setHealth(t.getHealth()-dmg);
                                                }
                                                if(t.getHealth() <= 0)
                                                {
                                                    deadTanks.add(t);
                                                    t.setDead(true);
                                                    canMove = false;
                                                    widthCounter = imgScale*Tank.tankImg.getWidth(null)/incAdjustment+1;
                                                    break;
                                                }
                                                if(gridType != 0)
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
                                    }
                                    //System.out.println(numUnits + ", bucketsize: " + bucket.size()); //Efficiency of above collision algorithms
                                    if(canMove)
                                    {
                                        /*int numGridPushed = 0;
                                        for(int i = (int)(dat[0]-300); i < (int)(dat[0]+300); i++)
                                        {
                                            for(int j = (int)(dat[1]-300); j < (int)(dat[1]+300); j++)
                                            {
                                                if(!h.discoveredGrid[i][j])
                                                {
                                                    int type = grid[i][j]%100;
                                                    if(type != 0)
                                                    {
                                                        h.out.println("GRID/" + i + "/" + j + "/" + type);
                                                        System.out.println("omfg why " + i + "," + j);
                                                    }
                                                    h.discoveredGrid[i][j] = true;
                                                    numGridPushed ++;
                                                }
                                            }
                                        }
                                        System.out.println(numGridPushed);*/
                                        t.update(System.currentTimeMillis()-frameTimer);
                                    }
                                    else
                                        for(Handler hh : handlers.values())
                                        {
                                            hh.out.println("SPEEDUPDATE/"+t.getName()+"/0/0");
                                        }
                                } catch(Exception exc) {
                                    exc.printStackTrace();
                                }
                                if(Math.abs(t.getX()-t.initX) < 85 && Math.abs(t.getY()-t.initY) < 95 && t.getHealth() < Tank.MAX_HEALTH)
                                {
                                    t.setHealth(t.getHealth()+(System.currentTimeMillis()-frameTimer)*0.05);
                                }make it so same team bases heal same team tanks!
                            }
                        }
                    }
                    
                    for(String s : handlers.keySet()) //one more tanks loop for writing stuff after updates
                    {
                        Handler h = handlers.get(s);
                        Tank t = tanks.get(s);
                        String toWrite = "";
                        for(String ss : handlers.keySet())
                        {
                            if(!ss.equals(s))
                            {
                                Tank tt = tanks.get(ss);
                                if(isVisible(t.getX(), t.getY(), tt.getX(), tt.getY()))
                                {
                                    if(t.setVisibility(ss, true))
                                        toWrite += "VIS/" + ss + "/true";
                                }
                                else
                                {
                                    if(t.setVisibility(ss, false))
                                        toWrite += "VIS/" + ss + "/false";
                                }
                            }
                        }
                        if(!toWrite.equals(""))
                            h.out.println(toWrite);
                        if(writer.length() > 1000)
                            h.out.println(writer);
                    }
                    if(writer.length() > 1000)
                        writer = "";
                    
                    for(Point p : bucket)
                    {
                        grid[(int)p.getX()][(int)p.getY()] /= 100;
                        grid[(int)p.getX()][(int)p.getY()] *= 100;
                        /*for(Handler h : handlers.values())
                        {
                            h.out.println("GRID/" + (int)p.getX() + "/" + (int)p.getY() + "/0");
                        }*/
                    }
                    for(Tank t : deadTanks)
                    {
                        numDeadOnTeams[t.getTeam()] += 1;
                        for(Handler h : handlers.values())
                        {
                            String clr = "/0,0,1/";
                            if(t.getTeam() == 1)
                                clr = "/0,1,0/";
                            h.out.println("SYSTEMMESSAGE/" + t.getName() + clr + " has died./1,0,0");
                        }
                    }
                    for(int num : numDeadOnTeams)
                        if(num == roomSize/2)
                        {
                            gameStartTime = System.currentTimeMillis()/1000-2000;
                        }
                    frameTimer = System.currentTimeMillis();
                    if(System.currentTimeMillis()-pushTimer > 500)
                    {
                        pushTimer = System.currentTimeMillis();
                        String writerino = "";
                        for(Tank t : tanks.values())
                            writerino += "PUSHTANK/" + t.getName() + "/" + (int)t.getX() + "/" + (int)t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + t.getTeam() + "/" + (int)t.getHealth();
                        for(Handler hh : handlers.values())
                        {
                            hh.out.println(writerino);
                        }
                    }
                    //System.out.println(Thread.activeCount());
                }
            }
        }
        
        public boolean isVisible(double x, double y, double x1, double y1)
        {
            double startX = x, startY = y, endX = x1, endY = y1;
            double theta = Math.atan2(endX-startX, endY-startY);
            theta -= Math.PI/2;
            theta = -theta;
            double xInc = Math.cos(theta);
            double yInc = Math.sin(theta);
            //mg.batch.draw(mg.sprites.get("shadow"), (float)tanks.get(mg.myTankName).getX(), (float)tanks.get(mg.myTankName).getY(), (float)(x1-tanks.get(mg.myTankName).getX()), (float)(y1-tanks.get(mg.myTankName).getY()));
            while(visibilityHelper(startX, startY, endX, endY, xInc, yInc))
            {
                int type = grid[(int)startX][(int)startY]%100;
                if(type != 0)
                {
                    return false;
                }
                startY += yInc;
                startX += xInc;
            }
            return true;
        }

        public boolean visibilityHelper(double sX, double sY, double eX, double eY, double xInc, double yInc)
        {
            if(xInc > 0) {
                if(yInc > 0) {
                    if(sX < eX || sY < eY) {
                        return true;
                    }
                }
                else {
                    if(sX < eX || sY > eY) {
                        return true;
                    }
                }
            } else {
                if(yInc > 0) {
                    if(sX > eX || sY < eY) {
                        return true;
                    }
                }
                else {
                    if(sX > eX || sY > eY) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
    
    private class Handler extends Thread {
        public String tName;
        public int team;
        private int numBullets=0;
        private Socket socket;
        private BufferedReader in;
        public PrintWriter out;
        public Room myRoom;
        private boolean nameSet = false, resetting = false;
        private int initX, initY;
        //public boolean[][] discoveredGrid; //this only needed if players are joining at different times in the game (which isn't happening)
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
        
        public void resetHandler()
        {
            try {
                myRoom = null;
                numBullets = 0;
                in.close();
                out.close();
                socket.close();
                resetting = false;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        @Override
        public void run() {
            try{
                out.println(hashCode());
                /*while(resetting)
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }*/
                //while(connections.contains(this))
                {
                    /*if(!nameSet)
                        out.println("SUBMITNAME");*/
                    while (!nameSet) {
                        String myName = in.readLine();
                        String[] special = myName.split("/");
                        if(special.length > 1 && special[1].equals(""+hashCode()))
                        {
                            myName = special[0];
                        }
                        boolean nameIsKeyword = false;
                        if (myName == null || myName.equals("") || myName.equals("null") || myName.contains("/") || myName.contains("'") || nameIsKeyword) {
                            out.println("SUBMITNAME/0");
                        }
                        else
                        {
                            for(String k : keywords)
                                if(myName.contains(k))
                                    nameIsKeyword = true;
                            if(nameIsKeyword) {
                                out.println("SUBMITNAME/0");
                            } else {
                                if(myName.equals("$)n2*$@n4208nn97g@$"))
                                {
                                    myName = "Admin";
                                }
                                boolean taken = false;
                                synchronized(connections) {
                                    for(Handler h : connections)
                                    {
                                        if(h.tName != null && h.tName.equals(myName))
                                        {
                                            if(special.length > 1)
                                                connections.remove(h);
                                            else
                                                taken = true;
                                            break;
                                        }
                                    }
                                }
                                if(taken)
                                {
                                    out.println("SUBMITNAME/1");
                                }
                                else
                                {
                                    nameSet = true;
                                    tName = myName;
                                    out.println("NAMEACCEPTED/"+tName);
                                    if(special.length > 1)
                                        System.out.println("Connection with " + tName + " re-established.");
                                    else
                                        System.out.println("Connection with " + tName + " established.");
                                }
                            }
                        }
                    }

                    boolean gameChosen = false;
                    while(!gameChosen)
                    {
                        String input = in.readLine();
                        if(input == null)
                            return;
                        Tank t = new Tank(tName, 0, 0);
                        if(input.equals("1V1"))
                        {
                            addToRoom(this, t, 0);
                            gameChosen = true;
                        }
                        else if(input.equals("3V3"))
                        {
                            addToRoom(this, t, 1);
                            gameChosen = true;
                        }
                        else if(input.equals("5V5"))
                        {
                            addToRoom(this, t, 2);
                            gameChosen = true;
                        }
                    }

                    /*new Thread()
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
                    }.start();*/

                    try {
                        Thread.sleep(1100); //wait for fading in client
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if(team == 1)
                        out.println("MESSAGE/You are on /1,1,1/green/0,1,0/ team./1,1,1");
                    else if(team == 0)
                        out.println("MESSAGE/You are on /1,1,1/blue/0,0,1/ team./1,1,1");
                    while (!myRoom.gameEnded) {
                        String input = in.readLine();
                        if (input == null) {
                            return;
                        }
                        if(input.equals("DONE"))
                            break;
                        Tank t = myRoom.tanks.get(tName);
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
                                double oldXSpeed = t.getXSpeed();
                                double oldYSpeed = t.getYSpeed();
                                if(t.getYSpeed() != 0 && t.getXSpeed() != 0)
                                {
                                    t.setXSpeed(t.getXSpeed()*Math.sqrt(2));
                                    t.setYSpeed(t.getYSpeed()*Math.sqrt(2));
                                }
                                if(!myRoom.phaseChanging)
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
                                            if(myRoom.phase == 1 && numBullets < 5 && t.getHealth() > 5)
                                            {
                                                int dmg = 25;
                                                t.setHealth(t.getHealth()-5);
                                                myRoom.bullets.add(new Bullet("bullet1", tName, myRoom.currBulID, t.getX()+Math.cos(t.getRotation())*15, t.getY()+Math.sin(t.getRotation())*15, Bullet.STANDARD_BULLET_SPEED, t.getRotation(), Bullet.STANDARD_BULLET_LIFETIME, dmg, false, false));
                                                numBullets += 1;
                                                myRoom.grid[(int)(t.getX()+Math.cos(t.getRotation())*15)][(int)(t.getY()+Math.sin(t.getRotation())*15)] += dmg*100;
                                                synchronized(myRoom.handlers.values()){
                                                    for(Handler h : myRoom.handlers.values()){
                                                        h.out.println("BULLET/bullet1/" +tName+ "/" + myRoom.currBulID + "/" + (t.getX()+Math.cos(t.getRotation())*15) + "/" + (t.getY()+Math.sin(t.getRotation())*15) + "/" + Bullet.STANDARD_BULLET_SPEED + "/" + t.getRotation() + "/" + Bullet.STANDARD_BULLET_LIFETIME + "/" + dmg + "/false/false");
                                                    }
                                                }
                                                ++myRoom.currBulID;
                                            }
                                            break;
                                        /*case "Q": //testing for phase change bugs
                                            myRoom.changePhase();
                                            break;*/
                                    }
                                if(t.getYSpeed() != 0 && t.getXSpeed() != 0)
                                {
                                    t.setXSpeed(t.getXSpeed()/Math.sqrt(2));
                                    t.setYSpeed(t.getYSpeed()/Math.sqrt(2));
                                }
                                if(oldXSpeed != t.getXSpeed() || oldYSpeed != t.getYSpeed())
                                    for(Handler hh : myRoom.handlers.values())
                                    {
                                        hh.out.println("SPEEDUPDATE/" + t.getName() + "/" + t.getXSpeed() + "/" + t.getYSpeed());
                                    }
                            }
                        }
                    }
                    resetting = true;
                    resetHandler();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (tName != null) {
                    if(myRoom != null) {
                        if(myRoom.gameStartTime == 0)
                        {
                            myRoom.handlers.remove(tName);
                            myRoom.tanks.remove(tName);
                        }
                        else
                        {
                            myRoom.tanks.get(tName).setHealth(-1);
                            for(Handler h : myRoom.handlers.values())
                            {
                                h.out.println("DISCONNECT/" + tName);
                            }
                        }
                    }
                    Thread.currentThread().interrupt();
                    try {
                        socket.close();
                        connections.remove(this);
                        System.out.println("Connection with " + tName + " has been terminated");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                else
                {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    connections.remove(this);
                }
            }
        }
    }
}