/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package echoserver;

/**
 *
 * @author Ashok
 */
public class Bullet
{
    private String name;
    
    private float x,y,xVel,yVel;
    private int direction, lifetime; //lifetime in millis
    
    public Bullet(String name, float x, float y)
    {
        new Bullet(name, x, y, 0, 0, 0, 3000);
    }
    
    public Bullet(String name, float x, float y, float xSpd, float ySpd, int direction, int lifetime)
    {
        this.name = name;
        this.x = x;
        this.y = y;
        xVel = xSpd;
        yVel = ySpd;
        this.direction = direction;
        this.lifetime = lifetime;
    }
    
    public void update()
    {
        x += xVel;
        y += yVel;
    }
    
    public String getName()
    {
        return name;
    }
    
    public float getX()
    {
        return x;
    }
    
    public float getY()
    {
        return y;
    }
    
    public float getDirection()
    {
        return direction;
    }
    
    public float getXSpeed()
    {
        return xVel;
    }
    
    public float getYSpeed()
    {
        return yVel;
    }
    
    public void setSpeed(float xSpd, float ySpd)
    {
        xVel = xSpd;
        yVel = ySpd;
    }
    
    public void setDirection(int dir)
    {
        direction = dir;
    }
    
    public void setX(float newX)
    {
        x = newX;
    }
    
    public void setY(float newY)
    {
        y = newY;
    }
}
