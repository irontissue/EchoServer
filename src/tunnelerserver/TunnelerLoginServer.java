/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tunnelerserver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 *
 * @author Ashok
 */
public class TunnelerLoginServer extends JFrame
{
    private static final int PORT = 8880;
    private static int porter = 8881;
    
    private static JTextArea j;
    
    private static ArrayList<TunnelerServer> servers;
    
    public TunnelerLoginServer()
    {
        setLayout(new BorderLayout());
        setName("Tunneler Server Manager");
        setTitle("Tunneler Server Manager");
        JPanel pp = new JPanel();
        JButton a = new JButton("Add server");
        a.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent ae) {
                if(servers.size() < 20)
                {
                    servers.add(new TunnelerServer(porter));
                    porter++;
                }
            }
        });
        pp.add(a);
        JButton r = new JButton("Remove server");
        r.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent ae) {
                int idx = 0;
                for(TunnelerServer s : servers)
                    if(s.getSize() < servers.get(idx).getSize())
                        idx = servers.indexOf(s);
                servers.get(idx).closed = true;
                servers.remove(idx);
            }
        });
        pp.add(r);
        add(pp, BorderLayout.PAGE_END);
        JPanel p = new JPanel();
        j = new JTextArea();
        p.add(j);
        add(p, BorderLayout.PAGE_START);
        setSize(400, 200);
        setBackground(Color.WHITE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        new Thread(){
            @Override
            public void run()
            {
                while(true)
                {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    String str = "";
                    for(int i = 0; i < servers.size(); i++)
                    {
                        str += servers.get(i).getPort() + "\t\t\t\t" + servers.get(i).getSize();
                        if(i != servers.size()-1)
                            str += "\n";
                    }
                    j.setText(str);
                }
            }
        }.start();
    }
    
    public static void main(String[] args)
    {
        System.out.println("The tunneler login server is running on port "+PORT+".");
        servers = new ArrayList<>();
        new TunnelerLoginServer();
        new Thread(){
            @Override
            public void run()
            {
                try (ServerSocket listener = new ServerSocket(PORT)) {
                    while (true) {
                        Socket s = listener.accept();
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                        TunnelerServer min = servers.get(0);
                        int port = min.getPort();
                        if(servers.size() > 1)
                            for(int i = 1; i < servers.size(); i++)
                            {
                                if(servers.get(i).getSize() < min.getSize())
                                {
                                    min = servers.get(i);
                                    port = min.getPort();
                                }
                            }
                        out.println(port);
                        out.close();
                        s.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }
}
