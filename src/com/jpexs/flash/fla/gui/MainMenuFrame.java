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

import com.jpexs.cfb.CompoundFileBinary;
import com.jpexs.flash.fla.converter.FlaConverter;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.streams.CfbOutputStorage;
import com.jpexs.flash.fla.converter.streams.DirectoryInputStorage;
import com.jpexs.flash.fla.converter.streams.InputStorageInterface;
import com.jpexs.flash.fla.converter.streams.OutputStorageInterface;
import com.jpexs.flash.fla.converter.streams.ZippedInputStorage;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author JPEXS
 */
public class MainMenuFrame extends JFrame {

    private static final String KEY_CONVERT_SRC_DIR = "convert.source.dir";
    private static final String KEY_CONVERT_DST_DIR = "convert.target.dir";
    private static final String KEY_EXTRACT_SRC_DIR = "extract.source.dir";
    private static final String KEY_EXTRACT_DST_DIR = "extract.target.dir";

    public MainMenuFrame() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Gui.saveConfig();
            }
        });
        setTitle("FLA ComDoc tools");
        Container cnt = getContentPane();
        cnt.setLayout(new GridLayout(4, 2));

        JButton convertCs4Button = new JButton("Convert to CS4...");
        convertCs4Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.CS4);
            }
        }
        );
        JButton convertCs3Button = new JButton("Convert to CS3...");
        convertCs3Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.CS3);
            }
        }
        );

        JButton convertF8Button = new JButton("Convert to Flash 8...");
        convertF8Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.F8);
            }
        }
        );
        
        JButton convertMX2004Button = new JButton("Convert to MX2004...");
        convertMX2004Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.MX2004);
            }
        }
        );
        
        JButton convertMXButton = new JButton("Convert to MX...");
        convertMXButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.MX);
            }
        }
        );
        
        JButton convertF5Button = new JButton("Convert to Flash 5...");
        convertF5Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convert(FlaFormatVersion.F5);
            }
        }
        );
        JButton extractButton = new JButton("Extract FLA ComDoc...");
        extractButton.addActionListener(this::extractActionPerformed);
        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(this::exitActionPerformed);

        cnt.add(convertCs4Button);
        cnt.add(convertCs3Button);
        cnt.add(convertF8Button);
        cnt.add(convertMX2004Button);
        cnt.add(convertMXButton);
        cnt.add(convertF5Button);
        cnt.add(extractButton);
        cnt.add(exitButton);

        Gui.setWindowIcon(this);
        setSize(400, 300);
        Gui.centerScreen(this);
    }

    private void convert(FlaFormatVersion flaFormatVersion) {
        JFileChooser ch = new JFileChooser(Gui.getConfigByKey(KEY_CONVERT_SRC_DIR, ""));
        ch.setDialogTitle("Select source CS5+ document");
        ch.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String n = f.getName().toLowerCase();
                return n.endsWith(".fla") || n.endsWith(".xfl");
            }

            @Override
            public String getDescription() {
                return "FLA documents CS5+ (*.fla; *.xfl)";
            }

        });
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        JFileChooser ch2 = new JFileChooser(Gui.getConfigByKey(KEY_CONVERT_DST_DIR, ""));
        ch2.setDialogTitle("Select destination FLA file");
        ch2.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                return f.getName().toLowerCase().endsWith(".fla");
            }

            @Override
            public String getDescription() {
                return "FLA document " + flaFormatVersion + " (*.fla)";
            }
        });

        if (ch2.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File inputFile = ch.getSelectedFile();
        File outputFile = ch2.getSelectedFile();

        Gui.setConfigByKey(KEY_CONVERT_SRC_DIR, inputFile.getParentFile().getAbsolutePath());
        Gui.setConfigByKey(KEY_CONVERT_DST_DIR, outputFile.getParentFile().getAbsolutePath());

        try {
            InputStorageInterface inputStorage;
            if (inputFile.getAbsolutePath().toLowerCase().endsWith(".xfl")) {
                inputStorage = new DirectoryInputStorage(inputFile.getParentFile());
            } else {
                inputStorage = new ZippedInputStorage(inputFile);
            }
            OutputStorageInterface outputStorage = new CfbOutputStorage(outputFile);

            FlaConverter contentsGenerator = new FlaConverter(flaFormatVersion);
            contentsGenerator.convert(inputStorage, outputStorage);

            inputStorage.close();
            outputStorage.close();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Conversion was successfull", "Info", JOptionPane.INFORMATION_MESSAGE);

    }

    private void extractActionPerformed(ActionEvent e) {
        JFileChooser ch = new JFileChooser(Gui.getConfigByKey(KEY_EXTRACT_SRC_DIR, ""));
        ch.setDialogTitle("Select source ComDoc FLA file");
        ch.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String n = f.getName().toLowerCase();
                return n.endsWith(".fla");
            }

            @Override
            public String getDescription() {
                return "FLA ComDoc documents - CS4 and lower (*.fla)";
            }

        });
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        JFileChooser ch2 = new JFileChooser(Gui.getConfigByKey(KEY_EXTRACT_DST_DIR, ""));
        ch2.setDialogTitle("Select destination directory");
        ch2.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        ch2.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory();
            }

            @Override
            public String getDescription() {
                return "Directories";
            }
        });

        if (ch2.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File inputFile = ch.getSelectedFile();
        File outputDir = ch2.getSelectedFile();

        Gui.setConfigByKey(KEY_EXTRACT_SRC_DIR, inputFile.getParentFile().getAbsolutePath());
        Gui.setConfigByKey(KEY_EXTRACT_DST_DIR, outputDir.getAbsolutePath());

        try {
            CompoundFileBinary cfb = new CompoundFileBinary(inputFile);
            cfb.extractTo("", outputDir);
            cfb.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Extraction was successfull", "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exitActionPerformed(ActionEvent e) {
        Gui.saveConfig();
        System.exit(0);
    }
}
