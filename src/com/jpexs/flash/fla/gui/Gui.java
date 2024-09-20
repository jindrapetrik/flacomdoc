/*
 * Copyright (C) 2024 JPEXS.
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.jpexs.flash.fla.gui;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author JPEXS
 */
public class Gui {

    private static GraphicsEnvironment env;

    private static final File UNSPECIFIED_FILE = new File("unspecified");

    private static File directory = UNSPECIFIED_FILE;

    private static final String SHORT_APPLICATION_NAME = "FlaComDoc";
    private static final String VENDOR = "JPEXS";

    private static final String CONFIG_NAME = "config.ini";

    private static final Map<String, String> configuration = new LinkedHashMap<>();

    private enum OSId {
        WINDOWS, OSX, UNIX
    }

    private static OSId getOSId() {
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
            return OSId.OSX;
        } else if (OS.indexOf("win") >= 0) {
            return OSId.WINDOWS;
        } else {
            return OSId.UNIX;
        }
    }

    public static void setWindowIcon(Window w) {
        List<Image> images = new ArrayList<>();
        images.add(loadImage("fla16"));
        images.add(loadImage("fla32"));
        w.setIconImages(images);
    }

    public static BufferedImage loadImage(String name) {
        URL imageURL = Gui.class.getResource("/com/jpexs/flash/fla/gui/graphics/" + name + ".png");
        try {
            return ImageIO.read(imageURL);
        } catch (IOException ex) {
            return null;
        }
    }

    public static void saveConfig() {
        try {
            String configFile = getConfigFile();
            PrintWriter writer = new PrintWriter(configFile);
            for (String key : configuration.keySet()) {
                writer.println(key + "=" + configuration.get(key));
            }
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            //ignore
        }
    }

    public static void loadConfig() {
        configuration.clear();
        try {
            String configFilePath = getConfigFile();
            File configFile = new File(configFilePath);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
                    String s;
                    while ((s = br.readLine()) != null) {
                        if (s.startsWith(";")) {
                            continue;
                        }
                        if (!s.contains("=")) {
                            continue;
                        }
                        String key = s.substring(0, s.indexOf("="));
                        String value = s.substring(s.indexOf("=") + 1);
                        configuration.put(key, value);
                    }
                }
            }
        } catch (IOException ex) {
            //ignore
        }
    }

    public static void setConfigByKey(String key, String value) {
        configuration.put(key, value);
    }

    public static String getConfigByKey(String key, String defaultValue) {
        if (configuration.containsKey(key)) {
            return configuration.get(key);
        }
        return defaultValue;
    }

    private static String getConfigFile() throws IOException {
        return getAppHome() + CONFIG_NAME;
    }

    public static String getAppHome() {
        if (directory == UNSPECIFIED_FILE) {
            directory = null;
            String userHome = null;
            try {
                userHome = System.getProperty("user.home");
            } catch (SecurityException ignore) {
                //ignored
            }
            if (userHome != null) {
                String applicationId = SHORT_APPLICATION_NAME;
                OSId osId = getOSId();
                if (osId == OSId.WINDOWS) {
                    File appDataDir = null;
                    try {
                        String appDataEV = System.getenv("APPDATA");
                        if ((appDataEV != null) && (appDataEV.length() > 0)) {
                            appDataDir = new File(appDataEV);
                        }
                    } catch (SecurityException ignore) {
                        //ignored
                    }
                    String vendorId = VENDOR;
                    if ((appDataDir != null) && appDataDir.isDirectory()) {
                        // ${APPDATA}\{vendorId}\${applicationId}
                        String path = vendorId + "\\" + applicationId + "\\";
                        directory = new File(appDataDir, path);
                    } else {
                        // ${userHome}\Application Data\${vendorId}\${applicationId}
                        String path = "Application Data\\" + vendorId + "\\" + applicationId + "\\";
                        directory = new File(userHome, path);
                    }
                } else if (osId == OSId.OSX) {
                    // ${userHome}/Library/Application Support/${applicationId}
                    String path = "Library/Application Support/" + applicationId + "/";
                    directory = new File(userHome, path);
                } else {
                    File xdgConfigHome = null;
                    File oldConfigDir = new File(userHome, "." + applicationId + "/");
                    try {
                        String xdgConfigHomeEV = System.getenv("XDG_CONFIG_HOME");
                        if ((xdgConfigHomeEV != null) && (xdgConfigHomeEV.length() > 0)) {
                            xdgConfigHome = new File(xdgConfigHomeEV);
                        }
                    } catch (SecurityException ignore) {
                        //ignored
                    }
                    if ((xdgConfigHome != null) && xdgConfigHome.isDirectory()) {
                        // ${xdgConfigHome}/${applicationId}
                        String path = applicationId + "/";
                        directory = new File(xdgConfigHome, path);
                    } else if (oldConfigDir.isDirectory()) {
                        // ${userHome}/.${applicationId}
                        directory = oldConfigDir;
                    } else {
                        // ${userHome}/.config/${applicationId}
                        String path = ".config/" + applicationId + "/";
                        directory = new File(userHome, path);
                    }
                }
            } else {
                //no home, then use application directory
                directory = new File(".");
            }
        }
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                if (!directory.exists()) {
                    directory = new File("."); //fallback to current directory
                }
            }
        }
        String ret = directory.getAbsolutePath();
        if (!ret.endsWith(File.separator)) {
            ret += File.separator;
        }
        return ret;
    }

    private static GraphicsEnvironment getEnv() {
        if (env == null) {
            env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        }
        return env;
    }

    public static void centerScreen(Window f) {
        int topLeftX;
        int topLeftY;
        int screenX;
        int screenY;
        int windowPosX;
        int windowPosY;
        GraphicsDevice device = getEnv().getDefaultScreenDevice();

        topLeftX = device.getDefaultConfiguration().getBounds().x;
        topLeftY = device.getDefaultConfiguration().getBounds().y;

        screenX = device.getDefaultConfiguration().getBounds().width;
        screenY = device.getDefaultConfiguration().getBounds().height;

        Insets bounds = Toolkit.getDefaultToolkit().getScreenInsets(device.getDefaultConfiguration());
        screenX = screenX - bounds.right;
        screenY = screenY - bounds.bottom;

        windowPosX = ((screenX - f.getWidth()) / 2) + topLeftX;
        windowPosY = ((screenY - f.getHeight()) / 2) + topLeftY;

        f.setLocation(windowPosX, windowPosY);
    }

    public static void start() {

        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("sun.java2d.uiScale", "1.0");
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.uiScale.enabled", "false");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException ex) {
            //ignore
        }

        loadConfig();
        MainMenuFrame mainMenuFrame = new MainMenuFrame();
        mainMenuFrame.setVisible(true);
    }
}
