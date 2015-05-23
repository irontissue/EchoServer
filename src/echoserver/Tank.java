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
public class Tank
{
    private String name;
    
    private float x,y,xVel,yVel;
    private int direction = 0; //0 to 7, 8 directions
    
    public Tank(String name, float x, float y)
    {
        this.name = name;
        this.x = x;
        this.y = y;
        xVel = 0;
        yVel = 0;
    }
    
    public void update()
    {
        x += xVel;
        y += yVel;
        if(xVel > 0)
        {
            if(yVel == 0)
            {
                direction = 0;
            }
            else if(yVel > 0)
            {
                direction = 1;
            }
            else
            {
                direction = 7;
            }
        }
        else if(xVel < 0)
        {
            if(yVel == 0)
            {
                direction = 4;
            }
            else if(yVel > 0)
            {
                direction = 3;
            }
            else
            {
                direction = 5;
            }
        }
        else
        {
            if(yVel > 0)
            {
                direction = 2;
            }
            else if(yVel < 0)
            {
                direction = 6;
            }
        }
    }
    
    public String getName()
    {
        return name;
    }

    public void setName(String nombre)
    {
        name = nombre;
    }
    
    public float getX()
    {
        return x;
    }
    
    public float getY()
    {
        return y;
    }
    
    public int getDirection()
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
    
    public void setXSpeed(float xSpd)
    {
        xVel = xSpd;
    }
    
    public void setYSpeed(float ySpd)
    {
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
