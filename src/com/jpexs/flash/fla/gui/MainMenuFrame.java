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
import com.jpexs.flash.fla.converter.XflReader;
import com.jpexs.flash.fla.converter.BinaryFlaConverter;
import com.jpexs.flash.fla.converter.BinaryFlaConversionResult;
import com.jpexs.flash.fla.converter.FlaFormatVersion;
import com.jpexs.flash.fla.converter.FlaTargetVersion;
import com.jpexs.flash.fla.reader.BinaryFlaReader;
import com.jpexs.flash.fla.exporter.BinaryFlaToXflExporter;
import com.jpexs.flash.fla.exporter.XflExportResult;
import com.jpexs.flash.fla.exporter.XflVersionConverter;
import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
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
        setTitle("FLA converter");
        Container cnt = getContentPane();
        cnt.setLayout(new GridLayout(3, 1, 0, 4));

        JButton convertButton = new JButton("Convert FLA", getIcon("convert"));
        convertButton.addActionListener(e -> convert());
        JButton extractButton = new JButton("Extract binary FLA", getIcon("extract"));
        extractButton.addActionListener(this::extractActionPerformed);
        JButton exitButton = new JButton("Exit", getIcon("exit"));
        exitButton.addActionListener(this::exitActionPerformed);

        cnt.add(convertButton);
        cnt.add(extractButton);
        cnt.add(exitButton);

        Gui.setWindowIcon(this);
        setSize(300, 150);
        setResizable(false);
        Gui.centerScreen(this);
    }

    private ImageIcon getIcon(String name) {
        return new ImageIcon(MainMenuFrame.class.getClassLoader().getResource("com/jpexs/flash/fla/gui/graphics/" + name + ".png"));
    }

    private void convert() {
        JFileChooser ch = new JFileChooser(Gui.getConfigByKey(KEY_CONVERT_SRC_DIR, ""));
        ch.setDialogTitle("Select source FLA or XFL document");
        ch.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        ch.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String n = f.getName().toLowerCase();
                return n.endsWith(".fla") || n.endsWith(".spa") || n.endsWith(".xfl");
            }

            @Override
            public String getDescription() {
                return "FLA/XFL documents (*.fla; *.spa; *.xfl)";
            }

        });
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File inputFile = ch.getSelectedFile();
        JFileChooser ch2 = new JFileChooser(Gui.getConfigByKey(KEY_CONVERT_DST_DIR, "")) {
            @Override
            public void approveSelection() {
                File selected = getSelectedFile();
                if (selected == null) {
                    return;
                }
                ConversionFileFilter format = (ConversionFileFilter) getFileFilter();
                File destination = format.destination(selected);
                setSelectedFile(destination);
                if (format.directoryOutput && destination.exists()) {
                    JOptionPane.showMessageDialog(this, "Choose a new directory for the XFL project.",
                            "Directory already exists", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!format.directoryOutput && destination.isFile()
                        && JOptionPane.showConfirmDialog(this, "Replace " + destination.getName() + "?",
                                "Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    return;
                }
                super.approveSelection();
            }
        };
        ch2.setDialogTitle("Save converted document");
        ch2.setAcceptAllFileFilterUsed(false);
        FlaTargetVersion[] versions = FlaTargetVersion.values();
        for (int i = versions.length - 1; i >= 0; i--) {
            ch2.addChoosableFileFilter(new ConversionFileFilter(versions[i], false));
            if (!versions[i].isBinary()) {
                ch2.addChoosableFileFilter(new ConversionFileFilter(versions[i], true));
            }
        }
        ch2.setFileFilter(ch2.getChoosableFileFilters()[0]);
        ch2.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, e -> {
            ConversionFileFilter format = (ConversionFileFilter) ch2.getFileFilter();
            ch2.setFileSelectionMode(format.directoryOutput ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        });
        ch2.setSelectedFile(new File(ch2.getCurrentDirectory(), ConversionFileFilter.baseName(inputFile.getName())));
        installFileTypeIcons(ch2);
        if (ch2.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        ConversionFileFilter format = (ConversionFileFilter) ch2.getFileFilter();
        FlaTargetVersion targetVersion = format.version;
        boolean directoryOutput = format.directoryOutput;
        FlaFormatVersion flaFormatVersion = targetVersion.isBinary() ? targetVersion.getBinaryVersion() : null;
        File outputFile = ch2.getSelectedFile().getAbsoluteFile();

        Gui.setConfigByKey(KEY_CONVERT_SRC_DIR, inputFile.getAbsoluteFile().getParentFile().getAbsolutePath());
        Gui.setConfigByKey(KEY_CONVERT_DST_DIR, outputFile.getParentFile().getAbsolutePath());

        try {
            boolean binaryInput = inputFile.isFile() && BinaryFlaConverter.isBinaryFla(inputFile);
            if (!targetVersion.isBinary()) {
                XflExportResult result = binaryInput
                        ? new BinaryFlaToXflExporter(targetVersion.getXflVersion()).export(new BinaryFlaReader().read(inputFile))
                        : new XflVersionConverter(targetVersion.getXflVersion()).convert(inputFile);
                if (directoryOutput) {
                    result.writeToDirectory(outputFile);
                } else {
                    result.writeToZip(outputFile);
                }
                showConversionReport(result.getReport(), !result.getIssues().isEmpty());
                return;
            }
            com.jpexs.flash.fla.reader.BinaryFlaDocument source = binaryInput
                    ? new BinaryFlaReader().read(inputFile) : new XflReader().read(inputFile);
            BinaryFlaConversionResult result = new BinaryFlaConverter(flaFormatVersion, "WINDOWS-1252").convert(source);
            result.writeToFile(outputFile);
            showConversionReport(result.getReport(), !result.getIssues().isEmpty());
            return;
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + ex.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

    }

    private static void installFileTypeIcons(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox<?>) {
                JComboBox<?> combo = (JComboBox<?>) component;
                if (combo.getItemCount() > 0 && combo.getItemAt(0) instanceof ConversionFileFilter) {
                    installFileTypeRenderer(combo);
                }
            } else if (component instanceof Container) {
                installFileTypeIcons((Container) component);
            }
        }
    }

    private static <T> void installFileTypeRenderer(JComboBox<T> combo) {
        ListCellRenderer<? super T> original = combo.getRenderer();
        combo.setRenderer((list, value, index, selected, focused) -> {
            Component component = original.getListCellRendererComponent(list, value, index, selected, focused);
            if (component instanceof JLabel && value instanceof ConversionFileFilter) {
                ((JLabel) component).setIcon(((ConversionFileFilter) value).icon);
            }
            return component;
        });
    }

    /** A save-dialog file type binds a Flash version to its output container. */
    private static final class ConversionFileFilter extends FileFilter {

        final FlaTargetVersion version;
        final boolean directoryOutput;
        final Icon icon;

        ConversionFileFilter(FlaTargetVersion version, boolean directoryOutput) {
            this.version = version;
            this.directoryOutput = directoryOutput;
            String versionName = version == FlaTargetVersion.CS5_5 ? "CS5" : version.name();
            String iconName = "flash" + (versionName.startsWith("F") ? versionName.substring(1) : versionName).toLowerCase(Locale.ROOT);
            icon = new ImageIcon(Gui.loadImage(iconName).getScaledInstance(16, 16, Image.SCALE_SMOOTH));
        }

        private String extension() {
            return version == FlaTargetVersion.F1 ? ".spa" : ".fla";
        }

        static String baseName(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String extension : new String[]{".fla", ".spa", ".xfl"}) {
                if (lower.endsWith(extension)) {
                    return name.substring(0, name.length() - extension.length());
                }
            }
            return name;
        }

        File destination(File selected) {
            String name = selected.getName();
            if (directoryOutput) {
                return selected;
            }
            if (!name.toLowerCase(Locale.ROOT).endsWith(extension())) {
                return new File(selected.getParentFile(), baseName(name) + extension());
            }
            return selected;
        }

        @Override
        public boolean accept(File file) {
            return file.isDirectory() || !directoryOutput && file.getName().toLowerCase(Locale.ROOT).endsWith(extension());
        }

        @Override
        public String getDescription() {
            String name;
            switch (version) {
                case F1:
                    name = "FutureSplash Animator (Flash 1)";
                    break;
                case MX:
                    name = "Macromedia Flash MX";
                    break;
                case MX2004:
                    name = "Macromedia Flash MX 2004";
                    break;
                default:
                    name = version.name().startsWith("CS") ? "Adobe Flash " + version.name().replace('_', '.')
                            : "Macromedia Flash " + version.name().substring(1);
                    break;
            }
            return name + (directoryOutput ? " — XFL directory" : " (*" + extension() + ")");
        }
    }

    private void showConversionReport(String text, boolean limitations) {
        javax.swing.JTextArea report = new javax.swing.JTextArea(text, 18, 75);
        report.setEditable(false);
        report.setCaretPosition(0);
        JOptionPane.showMessageDialog(this, new javax.swing.JScrollPane(report), "Conversion completed",
                limitations ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private void extractActionPerformed(ActionEvent e) {
        JFileChooser ch = new JFileChooser(Gui.getConfigByKey(KEY_EXTRACT_SRC_DIR, ""));
        ch.setDialogTitle("Select source binary FLA file");
        ch.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String n = f.getName().toLowerCase();
                return n.endsWith(".fla") || n.endsWith(".spa");
            }

            @Override
            public String getDescription() {
                return "FLA binary documents - CS4 and lower (*.fla,*.spa)";
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
