/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package echoserver;

import java.awt.Image;
import java.awt.Toolkit;

/**
 *
 * @author Ashok
 */
public class Bullet
{
    private String name;
    
    public final static double STANDARD_BULLET_SPEED = (1.75*TunnelerServer.FRAME_RATE/1000.0); //pixels per millisecond.
    public final static int STANDARD_BULLET_LIFETIME = 3000;
    
    public static Image bulletImage = Toolkit.getDefaultToolkit().getImage("bullet1.png");
    
    private double x,y, speed;
    private double rotation;
    private int currLifetime = 0, lifetime; //lifetime in millis
    
    public Bullet(String name, float x, float y, float speed, double rotation, int lifetime)
    {
        this.name = name;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.lifetime = lifetime;
        this.speed = speed;
    }
    
    public void update(long deltaTime)
    {
        double xVel = speed*Math.cos(rotation);
        double yVel = speed*Math.sin(rotation);
        x += xVel*deltaTime;
        y += yVel*deltaTime;
        currLifetime += deltaTime;
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
