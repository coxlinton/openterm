/*
 * Copyright (c) 2016, 2017 Ascert, LLC.
 * www.ascert.com
 *
 * Based on original code from FreeHost3270, copyright for derivations from original works remain:
 *  Copyright (C) 1998, 2001  Art Gillespie
 *  Copyright (2) 2005 the http://FreeHost3270.Sourceforge.net
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.ascert.open.term.gui;

import com.ascert.open.term.application.OpenTermConfig;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.*;

import com.ascert.open.term.core.Host;

import com.ascert.open.term.i3270.Term3270;
import com.ascert.open.term.core.Terminal;
import com.ascert.open.term.core.TerminalFactoryRegistrar;

/**
 * Main application frame. TODO: Implement a paintField(field) and paintChar(RW3270Char) method
 */
//TODO - need a headless version i.e. one where no JFrame/Swing needed.
public class ApplicationFrame extends JFrame
    implements ActionListener, FocusListener
{

    private static final Logger log = Logger.getLogger(ApplicationFrame.class.getName());

    /**
     * These font sizes will be presented to the user.
     */
    public static final int[] FONT_SIZES =
    {
        6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 40
    };
    public static final String[] COLOR_NAMES =
    {
        "Black", "White", "Green", "Red", "Blue", "Orange", "Turquoise", "Dark Blue", "Light Green"
    };
    public static final Color[] COLOR_VALUES =
    {
        Color.BLACK, Color.WHITE, Color.GREEN, Color.RED, Color.BLUE,
        Color.ORANGE, Color.CYAN, new Color(0, 51, 102),
        new Color(204, 255, 204)
    };
    private JMenuBar menubar;
    private JMenu connect;
    private JToolBar toolbar;
    private JTerminalScreen rhp;
    private Terminal term;
    private List<Host> available = new ArrayList<>();
    private boolean autoConnect = true;
    private boolean embeddedUse = false;
    private String productName;

    /**
     * No-ops constructor. Asks users to enter the connection settings in the corresponding dialog box then proceeds as normal.
     */
    public ApplicationFrame()
    {
        this(new ArrayList<Host> (), null);
    }

    public ApplicationFrame(List<Host> available, JFrame parent)
    {
        super();
        this.available = available;
        init(parent);
    }

    // quick hack to allow a sort of "slave" mode that can be fed with data from outside
    public ApplicationFrame(Terminal term, boolean noConnection)
    {
        this.term = term;
        this.autoConnect = !noConnection;
        init(null);
    }

    // enabling embedded use ensures we don't try and exit the VM on window close
    public void setEmbeddedUse(boolean embeddedUse)
    {
        this.embeddedUse = embeddedUse;
    }

    public void actionPerformed(ActionEvent evt)
    {
        log.fine("dispatching action event");
    }

    public void disconnect()
    {
        term.disconnect();
    }

    /**
     * Shuts the application down.
     */
    public void exit()
    {
        exit(!embeddedUse);
    }

    public void exit(boolean exitVm)
    {
        dispose();
        if (exitVm)
        {
            //If a disconnected option is needed in embedded cases, it can be done manually
            if (term != null)
            {
                term.disconnect();
            }
            System.exit(0);
        }
    }

    public void focusGained(FocusEvent evt)
    {
        rhp.requestFocus();
    }

    public void focusLost(FocusEvent evt)
    {
    }

    public void processEvent(AWTEvent evt)
    {
        if (evt.getID() == Event.WINDOW_DESTROY)
        {
            exit();
        }

        super.processEvent(evt);
    }

    /**
     * Builds main menu. Constructs several menu items.
     */
    private void buildMainMenu()
    {
        menubar = new JMenuBar();
        setJMenuBar(menubar);
        /* Make sure that the menu does not trap F10, since we use it as PF10 */
        //TODO - probably want to do this also for Alt-F4 which acts as exit?
        InputMap inputMap = menubar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke("F10"), "none");

        JMenu file = new JMenu("Terminal");

        AbstractAction prtAct = new AbstractAction("Print")
        {
            public void actionPerformed(ActionEvent evt)
            {
                rhp.printScreen();
            }
        };
        file.add(prtAct);
        menubar.getActionMap().put("PRINT", prtAct);
        inputMap.put(KeyStroke.getKeyStroke("alt P"), "PRINT");

        file.addSeparator();

        file.add(new AbstractAction("Exit")
        {
            public void actionPerformed(ActionEvent evt)
            {
                ApplicationFrame.this.exit();
            }
        });
        menubar.add(file);

        connect = new JMenu("Connect");
        initHostsMenu();
        menubar.add(connect);

        JMenu options = new JMenu("Options");

        JMenu fonts = new JMenu("Font Size");
        ButtonGroup fontsGroup = new ButtonGroup();

        for (int i = 0; i < FONT_SIZES.length; i++)
        {
            int size = FONT_SIZES[i];

            JRadioButtonMenuItem sizeItem = new JRadioButtonMenuItem(new AbstractAction(
                Integer.toString(size, 10))
            {
                public void actionPerformed(ActionEvent evt)
                {
                    int size = Integer.parseInt(evt.getActionCommand(),
                                                10);
                    fontSize((float) size);
                }
            });

            if (size == JTerminalScreen.DEFAULT_FONT_SIZE)
            {
                sizeItem.setSelected(true);
            }

            fonts.add(sizeItem);
            fontsGroup.add(sizeItem);
        }

        options.add(fonts);

        JMenu fontcolor = new JMenu("Font Color");
        JMenu dfFontColor = new JMenu("Default Font");
        ButtonGroup fontColorGroup = new ButtonGroup();

        for (int i = 0; i < COLOR_NAMES.length; i++)
        {
            String name = COLOR_NAMES[i];
            JRadioButtonMenuItem colorItem = new JRadioButtonMenuItem(new AbstractAction(
                name)
            {
                public void actionPerformed(ActionEvent evt)
                {
                    String name = evt.getActionCommand();

                    for (int idx = 0; idx < COLOR_NAMES.length;
                         idx++)
                    {
                        if (name.equals(COLOR_NAMES[idx]))
                        {
                            ApplicationFrame.this.rhp.setForegroundColor(COLOR_VALUES[idx]);
                        }
                    }
                }
            });

            if (COLOR_VALUES[i] == JTerminalScreen.DEFAULT_FG_COLOR)
            {
                colorItem.setSelected(true);
            }

            dfFontColor.add(colorItem);
            fontColorGroup.add(colorItem);
        }

        fontcolor.add(dfFontColor);

        JMenu bldFontColor = new JMenu("Bold Font");
        ButtonGroup bldFontGroup = new ButtonGroup();

        for (int i = 0; i < COLOR_NAMES.length; i++)
        {
            String name = COLOR_NAMES[i];
            JRadioButtonMenuItem colorItem = new JRadioButtonMenuItem(new AbstractAction(
                name)
            {
                public void actionPerformed(ActionEvent evt)
                {
                    String name = evt.getActionCommand();

                    for (int idx = 0; idx < COLOR_NAMES.length;
                         idx++)
                    {
                        if (name.equals(COLOR_NAMES[idx]))
                        {
                            ApplicationFrame.this.rhp.setBoldColor(COLOR_VALUES[idx]);
                        }
                    }
                }
            });

            if (COLOR_VALUES[i] == JTerminalScreen.DEFAULT_BOLD_COLOR)
            {
                colorItem.setSelected(true);
            }

            bldFontColor.add(colorItem);
            bldFontGroup.add(colorItem);
        }

        fontcolor.add(bldFontColor);
        options.add(fontcolor);
        options.addSeparator();

        JMenu bgcolor = new JMenu("Background Color");
        ButtonGroup bgcolorGroup = new ButtonGroup();

        for (int i = 0; i < COLOR_NAMES.length; i++)
        {
            String name = COLOR_NAMES[i];
            JRadioButtonMenuItem colorItem = new JRadioButtonMenuItem(new AbstractAction(
                name)
            {
                public void actionPerformed(ActionEvent evt)
                {
                    String name = evt.getActionCommand();

                    for (int idx = 0; idx < COLOR_NAMES.length;
                         idx++)
                    {
                        if (name.equals(COLOR_NAMES[idx]))
                        {
                            ApplicationFrame.this.rhp.setBackgroundColor(COLOR_VALUES[idx]);
                        }
                    }
                }
            });

            if (COLOR_VALUES[i] == JTerminalScreen.DEFAULT_BG_COLOR)
            {
                colorItem.setSelected(true);
            }

            bgcolor.add(colorItem);
            bgcolorGroup.add(colorItem);
        }

        options.add(bgcolor);
        options.addSeparator();
        JCheckBoxMenuItem optFkeyBar = new JCheckBoxMenuItem("Show F-Key bar");
        optFkeyBar.setSelected("true".equals(OpenTermConfig.getProp("toolbar.fkey.enabled")));
        optFkeyBar.addItemListener(new ItemListener()
        {
            @Override
            public void itemStateChanged(ItemEvent e)
            {
                OpenTermConfig.setProp("toolbar.fkey.enabled", Boolean.toString(optFkeyBar.isSelected()));
                rhp.refresh();
                pack();
            }
        });
        options.add(optFkeyBar);

        options.addSeparator();
        options.add(new AbstractAction("Clear Saved Preferences")
            {
                public void actionPerformed(ActionEvent evt)
                {
                    int n = JOptionPane.showConfirmDialog(ApplicationFrame.this,
                                                          "Are you sure you want to clear all saved preferences?",
                                                          "Clear Preferences", JOptionPane.YES_NO_OPTION);   
                    if (n == JOptionPane.YES_OPTION)
                    {
                        OpenTermConfig.clearPrefs();
                    }
                }
            });
        
        
        
        menubar.add(options);

        JMenu about = new JMenu("Help");
        menubar.add(about);
        about.add(new AbstractAction("About")
        {
            public void actionPerformed(ActionEvent evt)
            {
                AboutDialog about = new AboutDialog(ApplicationFrame.this, productName);
                about.setVisible(true);
            }
        });

        //This is not ideal as initially it shows a blank screen but with 3270 status bar.
        //Be nicer to show something more "neutral"
        if (term == null)
        {
            term = new Term3270();
        }
        rhp = new JTerminalScreen(term);
        add("Center", rhp);
    }

    private void fontSize(float size)
    {
        rhp.setFont(rhp.getFont().deriveFont(size));
        revalidate();
        repaint();
        pack();
    }

    public void init(String host, int port, String type, boolean encryption)
    {
        log.fine("** host " + host);
        available.add(new Host(host, port, type, encryption));
        init(null);
    }

    /**
     * Performs operations neccessary to construct main application frame.
     *
     * @param host      DOCUMENT ME!
     * @param port      DOCUMENT ME!
     * @param available DOCUMENT ME!
     * @param parent    DOCUMENT ME!
     */
    private void init(JFrame parent)
    {
        productName = OpenTermConfig.getProp("product.name", "open.term");
        setTitle(productName);
        setResizable(false);
        setLayout(new BorderLayout());

        buildMainMenu();
        validate();
        repaint();
        pack();

        // Center on screen
        Dimension screen_size;
        Dimension frame_size;
        screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        frame_size = this.getSize();

        int offX = frame_size.width;
        int offY = frame_size.height;

        // If we have parent component, offset the new window from it (cascade windows)
        if (parent != null)
        {
            setLocation(parent.getLocation().x + 20, parent.getLocation().y + 20);
        }
        else
        {
            setLocation((screen_size.width - offX) / 2, (screen_size.height - offY) / 2);
        }

        // Connect to the first host in the set of available hosts in
        // case if there is only one host available
        if (available.size() == 1 && autoConnect)
        {
            connect(available.get(0));
        }
        else
        {
            setTitle(productName + " - Not Connected");
        }

        addFocusListener(this);
    }

    protected void initHostsMenu()
    {
        connect.removeAll();
        Iterator<Host> hosts = available.iterator();

        //JMenuItem mnu = new JMenuItem();
        //Font boldFont = menu.getFont().deriveFont(Font.BOLD);
        
        while (hosts.hasNext())
        {
            final Host hst = hosts.next();
            // quick hack for now - tooltips maybe neater
            String menuTxt = hst.toString();
            connect.add(new AbstractAction(menuTxt)
            {
                public void actionPerformed(ActionEvent evt)
                {
                    ApplicationFrame.this.disconnect();

                    try
                    {
                        ApplicationFrame.this.connect(hst);
                    }
                    catch (Exception e)
                    {
                        showConnectionErrorDialog(e.getMessage());
                    }
                }
            });
        }

        connect.add(new AbstractAction("New ...")
        {
            public void actionPerformed(ActionEvent evt)
            {
                NewHostDialog cfgDialog = new NewHostDialog();
                cfgDialog.setVisible(true);
                final Host newHost = cfgDialog.getHost();
                
                if (newHost != null)
                {
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            ApplicationFrame.this.disconnect();

                            try
                            {
                                ApplicationFrame.this.connect(newHost);
                            }
                            catch (Exception e)
                            {
                                showConnectionErrorDialog(e.getMessage());
                            }
                            
                            // Add to list regardless of connection success
                            if (newHost.isFavourite())
                            {
                                //TODO - could check if already present to avoid duplicates
                                available.add(newHost);
                                OpenTermConfig.setProp("favourite.hosts", Host.getFavouritesAsConfigString(available));
                                initHostsMenu();
                            }
                        }
                    });


                    rhp.requestFocusInWindow();
                }
            }
        });

        connect.addSeparator();
        connect.add(new AbstractAction("Disconnect")
        {
            public void actionPerformed(ActionEvent evt)
            {
                ApplicationFrame.this.disconnect();
            }
        });
        
        connect.addSeparator();
        connect.add(new AbstractAction("Organize Favourites")
        {
            public void actionPerformed(ActionEvent evt)
            {
                FavouriteHostsPanel pnlHost = new FavouriteHostsPanel(available);
                int res = JOptionPane.showConfirmDialog(ApplicationFrame.this, pnlHost, "Favourite Hosts", 
                                                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                
                if (res == JOptionPane.OK_OPTION)
                {
                    final List<Host> newHosts = pnlHost.getHosts();
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            available.clear();
                            available.addAll(newHosts);
                            OpenTermConfig.setProp("favourite.hosts", Host.getFavouritesAsConfigString(available));
                            initHostsMenu();
                        }
                    });
                }
            }
        });
    }
    
    protected void connect(Host host)
    {
        log.fine("** connect " + host);

        setTitle(productName + " - Connecting to " + host.toString());

        try
        {
            term = TerminalFactoryRegistrar.createTerminal(host.getTermType());
            rhp.setTerminal(term);

            repaint();
            pack();

            term.connect(host.getHostName(), host.getPort(), host.isEncryption());
            requestFocus();
            setTitle(productName + " - Connected to " + host.toString());
        }
        catch (Exception e)
        {
            showConnectionErrorDialog(e.getMessage());
        }
    }

    private void showConnectionErrorDialog(String message)
    {
        JOptionPane.showMessageDialog(rhp,
                                      "Failed to connect to the server:\n" + message,
                                      "Connection failure", JOptionPane.WARNING_MESSAGE);
    }

}