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
public class Tank
{
    private String name;
    
    public final static double TANK_SPEED = (1.0*TunnelerServer.FRAME_RATE/1000.0); //pixels per millisecond. The first number is pixels/frame.
    public final static double TANK_ROTATION_SPEED = Math.PI/1000; //radians per millisecond
    
    public static Image tankImg = Toolkit.getDefaultToolkit().getImage("resources/tank1.png");
    
    public static float imgScale = 6;
    
    private double x,y,xVel,yVel, speed = 0, rotation = 0, rotationSpeed = 0;
    
    public Tank(String name, float x, float y)
    {
        this.name = name;
        this.x = x;
        this.y = y;
        xVel = 0;
        yVel = 0;
    }
    
    public void update(long deltaTime)
    {
        x += xVel*deltaTime;
        y += yVel*deltaTime;
        if(xVel > 0)
        {
            if(yVel == 0)
            {
                rotation = 0;
            }
            else if(yVel > 0)
            {
                rotation = Math.PI/4;
            }
            else
            {
                rotation = Math.PI*7/4;
            }
        }
        else if(xVel < 0)
        {
            if(yVel == 0)
            {
                rotation = Math.PI;
            }
            else if(yVel > 0)
            {
                rotation = Math.PI*3/4;
            }
            else
            {
                rotation = Math.PI*5/4;
            }
        }
        else
        {
            if(yVel > 0)
            {
                rotation = Math.PI/2;
            }
            else if(yVel < 0)
            {
                rotation = Math.PI*3/2;
            }
        }
    }
    
    public double[] mockUpdate(long deltaTime)
    {
        double mockX = x, mockY = y, mockRotation = rotation;
        mockX += xVel*deltaTime;
        mockY += yVel*deltaTime;
        if(xVel > 0)
        {
            if(yVel == 0)
            {
                mockRotation = 0;
            }
            else if(yVel > 0)
            {
                mockRotation = Math.PI/4;
            }
            else
            {
                mockRotation = Math.PI*7/4;
            }
        }
        else if(xVel < 0)
        {
            if(yVel == 0)
            {
                mockRotation = Math.PI;
            }
            else if(yVel > 0)
            {
                mockRotation = Math.PI*3/4;
            }
            else
            {
                mockRotation = Math.PI*5/4;
            }
        }
        else
        {
            if(yVel > 0)
            {
                mockRotation = Math.PI/2;
            }
            else if(yVel < 0)
            {
                mockRotation = Math.PI*3/2;
            }
        }
        double[] d = {mockX,mockY,mockRotation};
        return d;
    }
    
    public void updateR(long deltaTime)
    {
        rotation += rotationSpeed*deltaTime;
        if(rotation >= 360)
        {
            rotation -= 360;
        }
        else if(rotation < 0)
        {
            rotation += 360;
        }
        double myXVel = Math.cos(rotation)*speed*deltaTime;
        double myYVel = Math.sin(rotation)*speed*deltaTime;
        x += myXVel;
        y += myYVel;
    }
    
    public double[] mockUpdateR(long deltaTime)
    {
        double mockRotation = rotation, mockX = x, mockY = y;
        mockRotation += rotationSpeed*deltaTime;
        if(mockRotation >= 360)
        {
            mockRotation -= 360;
        }
        else if(mockRotation < 0)
        {
            mockRotation += 360;
        }
        double myXVel = Math.cos(mockRotation)*TANK_SPEED*deltaTime;
        double myYVel = Math.sin(mockRotation)*TANK_SPEED*deltaTime;
        mockX += myXVel;
        mockY += myYVel;
        double[] d = {mockX,mockY,mockRotation};
        return d;
    }
    
    public String getName()
    {
        return name;
    }

    public void setName(String nombre)
    {
        name = nombre;
    }
    
    public double getX()
    {
        return x;
    }
    
    public double getY()
    {
        return y;
    }
    
    public double getXSpeed()
    {
        return xVel;
    }
    
    public double getYSpeed()
    {
        return yVel;
    }
    
    public double getSpeed()
    {
        return speed;
    }
    
    public double getRotation()
    {
        return rotation;
    }
    
    public double getRotationSpeed()
    {
        return rotationSpeed;
    }
    
    public void setXSpeed(double xSpd)
    {
        xVel = xSpd;
    }
    
    public void setYSpeed(double ySpd)
    {
        yVel = ySpd;
    }
    
    public void setX(double newX)
    {
        x = newX;
    }
    
    public void setY(double newY)
    {
        y = newY;
    }
    
    public void setRotation(double newRot)
    {
        rotation = newRot;
    }
    
    public void setRotationSpeed(double rotSpd)
    {
        rotationSpeed = rotSpd;
    }
    
    public void setSpeed(double newSpd)
    {
        speed = newSpd;
    }
    
    public void rotate(double angle)
    {
        rotation += angle;
    }
}
