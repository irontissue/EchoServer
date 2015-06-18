/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tunnelerserver;

import java.awt.Image;
import java.awt.Toolkit;

/**
 *
 * @author Ashok
 */
public class Bullet
{
    private String name;
    
    public final static float STANDARD_BULLET_SPEED = (float) (1.75*TunnelerServer.FRAME_RATE/1000.0); //pixels per millisecond. First number is pixels/frame.
    public final static int STANDARD_BULLET_LIFETIME = 3000;
    
    public static Image bulletImage = Toolkit.getDefaultToolkit().getImage("bullet1.png");
    
    private double x,y, speed;
    private double rotation;
    private int damage;
    private int currLifetime = 0, lifetime; //lifetime in millis
    
    public boolean piercesWalls, piercesTanks;
    
    public Bullet(String name, double x, double y, double speed, double rotation, int lifetime, int damage, boolean piercesWalls, boolean piercesTanks)
    {
        this.name = name;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.lifetime = lifetime;
        this.speed = speed;
        this.damage = damage;
        this.piercesTanks = piercesTanks;
        this.piercesWalls = piercesWalls;
    }
    
    public boolean update(long deltaTime) //returns true if alive, false if dead
    {
        double xVel = speed*Math.cos(rotation);
        double yVel = speed*Math.sin(rotation);
        x += xVel*deltaTime;
        y += yVel*deltaTime;
        currLifetime += deltaTime;
        return lifetime >= currLifetime;
    }
    
    public double[] mockUpdate(long deltaTime)
    {
        double[] d = new double[3];
        double xVel = speed*Math.cos(rotation);
        double yVel = speed*Math.sin(rotation);
        d[0] = x + xVel*deltaTime;
        d[1] = y + yVel*deltaTime;
        d[2] = currLifetime + deltaTime;
        return d;
    }
    
    public String getName()
    {
        return name;
    }
    
    public double getX()
    {
        return x;
    }
    
    public double getY()
    {
        return y;
    }
    
    public double getRotation()
    {
        return rotation;
    }
    
    public double getSpeed()
    {
        return speed;
    }
    
    public int getLifetime()
    {
        return lifetime;
    }
    
    public int getCurrentLifetime()
    {
        return currLifetime;
    }
    
    public int getDamage()
    {
        return damage;
    }
    
    public void setSpeed(double newSpeed)
    {
        speed = newSpeed;
    }
    
    public void setRotation(double dir)
    {
        rotation = dir;
    }
    
    public void setX(double newX)
    {
        x = newX;
    }
    
    public void setY(double newY)
    {
        y = newY;
    }
}
