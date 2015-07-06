package tunnelerserver;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TunnelerServer {
    private static final int PORT = 8888;
    public static final int FRAME_RATE = 30;
    private static String[] keywords = {"LOGIN", "CREATE", "SUBMITNAME", "NAMEACCEPTED", "DISCONNECT", "GRID", "PHASE", "BULLET", "SYSTEMMESSAGE", "MESSAGE", "PUSHTANK"};
    private static PrintWriter f;
    private static HashSet<Handler> connections = new HashSet();
    private static Room curr2PRoom, curr6PRoom, curr10PRoom;
    
    public static void main(String[] args) throws IOException
    {
        curr2PRoom = new Room(2);
        curr6PRoom = new Room(6);
        curr10PRoom = new Room(10);
        
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
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                Handler h = new Handler(listener.accept());
                h.start();
                connections.add(h);
            }
        }
    }
    
    public static void addToRoom(Handler h, Tank t, int roomID)
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
    
    private static class Room extends Thread{
        private int phase = 0; //0 = tunneling, 1 = shooting
        private int roomSize;
        private int numTeams = 2;
        private int teamSetter = 0;
        private int[] numDeadOnTeams;
        
        private int gameLength = 120; //seconds
        
        private boolean gameEnded = false;
        private boolean phaseChanging = false;
        
        private long pushTimer; //ms
        private long frameTimer; //ms
        private long gameStartTime; //seconds
        
        private float imgScale = 3;

        public HashMap<String, Tank> tanks = new HashMap();
        public HashMap<String, Handler> handlers = new HashMap(); //if only java default pkg had dictionaries like python!
        
        private ArrayList<Bullet> bullets = new ArrayList();

        private int[][] grid; //values in grid represent tile type. 0 is nothing, 1 is diggable dirt, 2 is wall.
                                                    //If value is above 2, there is a bullet there with damage = value/100.
                                                    //Essentially, a grid value 100 or above means theres damage there.
        
        public Room(int size)
        {
            roomSize = size;
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
                    h.out.println("PUSHTANK/" + t.getName() + "/" + (int)t.getX() + "/" + (int)t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + t.getTeam() + "/" + t.getHealth());
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
            handlers.put(h.tName, h);
            tanks.put(h.tName, t);
            String msg = " Waiting for " + (roomSize-handlers.size()) + " more players...";
            if(isFull())
                msg = "";
            for(Handler hh : handlers.values())
            {
                hh.out.println("SYSTEMMESSAGE/" + t.getName() + " has joined the game." + msg);
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
            for(Handler h : handlers.values())
            {
                h.out.println("PHASE/" + phase);
                tanks.get(h.tName).setXSpeed(0);
                tanks.get(h.tName).setYSpeed(0);
                tanks.get(h.tName).setX(h.initX);
                tanks.get(h.tName).setY(h.initY);
            }
            try{
                Thread.sleep(3000); //3sec for client to do fade in/out
            } catch(Exception ex) {
                ex.printStackTrace();
            }
            phaseChanging = false;
        }
        
        @Override
        public void run()
        {
            try
            {
                Thread.sleep(1500); //Wait 1.5 sec before countdown in case stuff is still happening
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
                        gameStartTime = System.currentTimeMillis()/1000;
                        gameLength = 180;
                        changePhase();
                    }
                    else
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
                                msg = "Team " + team + " has won!";
                        }
                        synchronized(handlers.values()){
                            for(Handler h : handlers.values())
                            {
                                h.out.println("SYSTEMMESSAGE/The game is over. " + msg);
                                //h.out.println("SYSTEMMESSAGE/"+msg);
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
                    Stack<Point> bucket = new Stack();
                    HashSet<Tank> deadTanks = new HashSet();
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
                                        grid[(int)dat[0]][(int)dat[1]] += b.getDamage()*10; //ricochet bullets do approximately 1/10 dmg. Any bullets < 10 damage will have ricochet that does 0 damage.
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
                                    int radius = (int)(imgScale*Tank.tankImg.getWidth(null)/2+3);
                                    double incAdjustment = 1;
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
                                                    startX = (int)(dat[0]+radius+1);
                                                    break;
                                                }
                                            }
                                        }
                                        bucket.addAll(tempBucket);
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
                                                            handlers.get(b.getName().split(" ",2)[1]).numBullets-=1;
                                                            break;
                                                        }
                                                    }
                                                    t.setHealth(t.getHealth()-dmg);
                                                    if(t.getHealth() <= 0)
                                                    {
                                                        deadTanks.add(t);
                                                        t.setDead(true);
                                                        canMove = false;
                                                        widthCounter = imgScale*Tank.tankImg.getWidth(null)/incAdjustment+1;
                                                        break;
                                                    }
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
                                if(System.currentTimeMillis()-pushTimer > 1000) //comment out this line to have absolute updating every frame (high cost). For now it updates every second for precision on clients. Speed of tanks is updated every keystroke change.
                                    for(String ss : handlers.keySet())
                                    {
                                        Handler hh = handlers.get(ss);
                                        hh.out.println("PUSHTANK/" + t.getName() + "/" + (int)t.getX() + "/" + (int)t.getY() + "/" + t.getXSpeed() + "/" + t.getYSpeed() + "/" + t.getRotation() + "/" + t.getTeam() + "/" + t.getHealth());
                                    }
                            }
                        }
                    }
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
                            h.out.println("SYSTEMMESSAGE/" + t.getName() + " has died.");
                        }
                    }
                    for(int num : numDeadOnTeams)
                        if(num == roomSize/2)
                        {
                            gameStartTime = System.currentTimeMillis()/1000-200;
                        }
                    frameTimer = System.currentTimeMillis();
                    if(System.currentTimeMillis()-pushTimer > 1000)
                        pushTimer = System.currentTimeMillis();
                    //System.out.println(Thread.activeCount());
                }
            }
        }
    }
    
    private static class Handler extends Thread {
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
                        if(special.length > 1)
                            myName = special[0];
                        boolean nameIsKeyword = false;
                        if (myName == null || myName.equals("") || myName.equals("null") || myName.contains("/") || nameIsKeyword) {
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
                    out.println("MESSAGE/You are on team " + team + ".");
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
                                            if(myRoom.phase == 1 && numBullets < 5)
                                            {
                                                myRoom.bullets.add(new Bullet("bullet1 "+tName, t.getX()+Math.cos(t.getRotation())*12, t.getY()+Math.sin(t.getRotation())*12, Bullet.STANDARD_BULLET_SPEED, t.getRotation(), Bullet.STANDARD_BULLET_LIFETIME, 25, false, false));
                                                numBullets += 1;
                                                myRoom.grid[(int)(t.getX()+Math.cos(t.getRotation())*15)][(int)(t.getY()+Math.sin(t.getRotation())*15)] += 2500;
                                                synchronized(myRoom.handlers.values()){
                                                    for(Handler h : myRoom.handlers.values()){
                                                        h.out.println("BULLET/bullet1 " +tName+ "/" + (t.getX()+Math.cos(t.getRotation())*15) + "/" + (t.getY()+Math.sin(t.getRotation())*15) + "/" + Bullet.STANDARD_BULLET_SPEED + "/" + t.getRotation() + "/" + Bullet.STANDARD_BULLET_LIFETIME + "/" + 25 + "/false/false");
                                                    }
                                                }
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
                        myRoom.handlers.remove(tName);
                        myRoom.tanks.remove(tName);
                        for(Handler h : myRoom.handlers.values())
                        {
                            h.out.println("DISCONNECT/" + tName);
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