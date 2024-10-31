/*
Kevin Terretaz
241031 LUT Finder 0.91
*/
import ij.*;
import ij.CompositeImage;
import ij.process.*;
import ij.plugin.LutLoader;
import ij.plugin.PlugIn;
import ij.gui.HTMLDialog;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import javax.swing.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.awt.image.IndexColorModel;

public class LUTs_Finder implements PlugIn {
    private static final String[] COLUMN_NAMES = new String[]{"LUT Name", "Preview","Estimated Description" , "Colors"};

    @Override
    public void run(String arg) {
        SwingUtilities.invokeLater(() -> create_and_show_GUI());
    }

    private void create_and_show_GUI() {
        String[] lut_List = IJ.getLuts();
        Object[][] table_Data = new Object[lut_List.length][4];
        for (int i = 0; i < lut_List.length; i++) {
            String lut_Name = lut_List[i];
            table_Data[i] = new Object[]{lut_Name, get_Lut_Icon(lut_Name), get_Lut_Infos(lut_Name), get_Lut_Colors(lut_Name)};
        }
        DefaultTableModel table_Model = get_Table_Model(table_Data);
        JTable table = new JTable(table_Model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                java.awt.Point point = event.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);
                if (column == 1) {
                    column = 0;
                }
                Object value = getValueAt(row, column);
                return (value != null) ? value.toString() : null;
            }
        };
        // Key Listener
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (table.getRowCount() > 0) {
                    int next_Row;
                    if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
                    next_Row = table.getSelectedRow();
                    // if Enter
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        apply_LUT(table);
                    }
                }
            }
        });
        // Mouse Listener for Double Click
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {  // Double-click detected
                    apply_LUT(table);
                }
            }
        });
        table.setDragEnabled(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setTransferHandler(get_Transfer_Handler());
        table.setRowHeight(26);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(1).setPreferredWidth(258);
        table.getColumnModel().getColumn(2).setPreferredWidth(290);
        table.getColumnModel().getColumn(3).setPreferredWidth(143);
        // Search Panel
        String help_Text = "<html><body style='background-color: #454545; color: #ffffff;'>" +
        "<strong>Applying a LUT:</strong><br>" +
        "_   Double-Click on a LUT name or press <strong>Enter</strong> to apply the selected LUT to your image.<br><br>" +
        "<strong>About LUTs preview bands:</strong><br>" +
        "_ The LUT images display bands of small value shift.<br>" +
        "_ Check for uniformity in color transitions for better contrast visibility.<br><br>" +
        "<strong>Description strings:</strong><br>" +
        "_ Each LUT comes with an estimated description of its properties:<ul>" +
            "<li><strong>Linear, Non-linear:</strong> Whether the brightness progression is linear.</li>" +
            "<li><strong>Diverging:</strong> Transitions from one color through a neutral midpoint to another color.</li>" +
            "<li><strong>Isoluminant:</strong> Changes in color but keeps the luminance consistent across the LUT.</li>" +
            "<li><strong>Cyclic:</strong> If the first and last colors are the same.</li>" +
            "<li><strong>Colorfulness:</strong> Multicolor or Monochrome.</li>" +
            "<li><strong>Basic:</strong> Identifies classic 'pure' LUTs (Red, Green, Blue, Cyan, etc.).</li>";
        JPanel search_Panel = new JPanel();
        // Filter bar
        JTextField search_Bar = new JTextField(30);
        search_Bar.setToolTipText("<html>Filter list by one or more properties (name, color, description)<br>add a '!' prefix to remove properties from the list. (i.e. ' !blue ')");
        TableRowSorter<TableModel> table_Sorter = new TableRowSorter<>(table_Model);
        table_Sorter.toggleSortOrder(2);
        table.setRowSorter(table_Sorter);
        search_Bar.getDocument().addDocumentListener(get_Filter_Listener(search_Bar, table_Sorter));
        search_Bar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (table.getRowCount() > 0) {
                    int next_Row;
                    if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
                    next_Row = table.getSelectedRow();
                    // if Enter
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) apply_LUT(table);
                    // if up
                    if (e.getKeyCode() == KeyEvent.VK_UP) {
                        next_Row = table.getSelectedRow() - 1;
                        if (next_Row > -1) table.setRowSelectionInterval(next_Row, next_Row);
                    }
                    // if down
                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        next_Row = table.getSelectedRow() + 1;
                        if (next_Row < table.getRowCount()) table.setRowSelectionInterval(next_Row, next_Row);
                    }
                }
            }
        });
        search_Panel.add(new JLabel("Filter :"));
        search_Panel.add(search_Bar);
        // clear button
        JButton reset_Button = new JButton("Clear");
        reset_Button.addActionListener(e -> search_Bar.setText(""));
        reset_Button.setToolTipText("Remove filter text");
        search_Panel.add(reset_Button);
        // about button
        JButton help_Button = new JButton("About");
        help_Button.addActionListener(e -> new HTMLDialog("About", help_Text, false));
        search_Panel.add(help_Button);
        // grey button
        JButton grey_Button = new JButton("Gray check");
        grey_Button.setToolTipText("Compare current LUT to gray");
        grey_Button.addMouseListener(new MouseAdapter() {
            LUT lut;
            public void mousePressed(MouseEvent e) {
                ImagePlus image = WindowManager.getCurrentImage();
                if (image == null) return;
                lut = image.getProcessor().getLut();
                IJ.run("Grays");
            }
            public void mouseReleased(MouseEvent e) {
                ImagePlus image = WindowManager.getCurrentImage();
                if (image == null) return;
                if (image.isComposite()) {
                    CompositeImage composite_Image = (CompositeImage) image;
                    composite_Image.setChannelLut(lut);
                } else {
                    image.getProcessor().setLut(lut);
                }
                image.updateAndDraw();
            }
        });
        search_Panel.add(grey_Button);
        // Create and display the JFrame
        JFrame frame = new JFrame("LUTs Finder");
        frame.setLayout(new BorderLayout());
        frame.add(search_Panel, BorderLayout.NORTH);
        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        frame.setLocation(300, 300);
        frame.setSize(900, 600);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    private static void apply_LUT(JTable table) {
        if (table.getSelectedRow() == -1) table.setRowSelectionInterval(0, 0);
        int selected_Row = table.getSelectedRow();
        String selected_LUT = (String) table.getValueAt(selected_Row, 0);
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            ImagePlus lut_Image = IJ.createImage(WindowManager.makeUniqueName(selected_LUT), "8-bit ramp", 256, 32, 1);
            lut_Image.show();
            IJ.run(selected_LUT);
            return;
        }
        if (!imp.isRGB()) IJ.run(selected_LUT);
        else {
            ImagePlus lut_Image = IJ.createImage(WindowManager.makeUniqueName(selected_LUT), "8-bit ramp", 256, 32, 1);
            lut_Image.show();
            IJ.run(selected_LUT);
        }
    }

    private static ImageIcon get_Lut_Icon(String lut_Name) {
        ImagePlus lut_Image = IJ.createImage("LUT icon", "8-bit ramp", 256, 24, 1);
        ImageProcessor ip = lut_Image.getProcessor();
        ip.setColorModel(LutLoader.getLut(lut_Name));
        for (int x = 0; x < 256; x++) {
            int gray_Value = x;
            int shifted_Value = (gray_Value + 10);
            for (int y = 15; y < 23; y++) {
                ip.putPixel(x, y, (x % 3 == 0 || x % 3 == 3) ? gray_Value : shifted_Value); // Stripes
            }
        }
        return new ImageIcon(ip.getBufferedImage());
    }

    private static String get_Lut_Colors(String lut_Name) {
        java.util.List<String> colors = new java.util.ArrayList<>();
        IndexColorModel lut = LutLoader.getLut(lut_Name);
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        lut.getBlues(blues);
        int step = 256 / 20;
        for (int i = 0; i < 256; i += step) {
            int red = reds[i] & 255;
            int green = greens[i] & 255;
            int blue = blues[i] & 255;
            String color_Name = get_Color_Name(red, green, blue);
            if (color_Name != null && !colors.contains(color_Name)) {
                colors.add(color_Name);
            }
        }
        return String.join(", ", colors);
    }

    private static String get_Color_Name(int r, int g, int b) {
        float[] hsv = Color.RGBtoHSB(r, g, b, null);
        float hue = hsv[0] * 360;  // Hue is between 0 and 1, so multiply by 360
        float saturation = hsv[1];  // Saturation between 0 and 1
        float value = hsv[2];  // Brightness (value) between 0 and 1
        // Detect black, gray, and white
        if (value < 0.05f) return "black";
        if (saturation < 0.15 && value >= 0.05 && value <= 0.9) return "gray";
        if (value > 0.9 && saturation < 0.12) return "white";
        // Classify color based on hue
        if ((hue >= 0 && hue <= 15) || (hue >= 340 && hue <= 360)) return "red";
        if (hue > 15 && hue <= 45) return "orange";
        if (hue > 45 && hue <= 75) return "yellow";
        if (hue > 75 && hue <= 150) return "green";
        if (hue > 150 && hue <= 200) return "cyan";
        if (hue > 200 && hue <= 260) return "blue";
        if (hue > 260 && hue <= 330) return "magenta";
        if (hue > 330 && hue < 340) return "pink";
        return null;
    }

    private static String get_Lut_Infos(String lut_Name) {
        IndexColorModel lut = LutLoader.getLut(lut_Name);
        byte[] reds = new byte[256];
        byte[] greens = new byte[256];
        byte[] blues = new byte[256];
        lut.getReds(reds);
        lut.getGreens(greens);
        lut.getBlues(blues);
        int[] lut_Luminance = get_Lutinance(reds, greens, blues);
        // Linearity
        String is_Linear = "Linear";
        int step = 20;
        java.util.List<Integer> luminance_Trend = new java.util.ArrayList<>();
        int n_Luminance_Shift = 0;
        if (lut_Luminance[2] >= lut_Luminance[0]) {
            for (int i = step; i < 256; i += step) {
                luminance_Trend.add((lut_Luminance[i] >= lut_Luminance[i - step]) ? 1 : -1);
            }
        } else {
            for (int i = step; i < 256; i += step) {
                luminance_Trend.add((lut_Luminance[i] <= lut_Luminance[i - step]) ? 1 : -1);
            }
        }
        int previous_Trend = luminance_Trend.get(0);
        for (int i = 1; i < luminance_Trend.size(); i++) {
            if (!luminance_Trend.get(i).equals(previous_Trend)) {
                is_Linear = "Non-uniform";
                n_Luminance_Shift++;
                previous_Trend = luminance_Trend.get(i);
            }
        }
        // Circularity
        String is_Cyclic = "Cyclic";
        int tolerance = 15;
        if (!(is_Near(reds[0], reds[255], tolerance) &&
            is_Near(greens[0], greens[255], tolerance) &&
            is_Near(blues[0], blues[255], tolerance))) {
            is_Cyclic = null;
        }
        // Isoluminance
        String is_Isoluminant = "Isoluminant";
        for (int i = 0; i < 256; i++) {
            if (Math.abs(lut_Luminance[i] - lut_Luminance[255]) > 40) {
                is_Isoluminant = null;
                break;
            }
        }
        if (is_Isoluminant != null) is_Linear = "Linear";
        if (lut_Name.equalsIgnoreCase("Blue")) is_Isoluminant = null; // pure blue is freaking dark..
        // Diverging
        String is_Diverging = "Diverging";
        if (lut_Luminance[255] < 60) is_Diverging = null;
        else {
            for (int i = 0; i < 128; i++) {
                if ((Math.abs(lut_Luminance[i] - lut_Luminance[255 - i]) > 50)) {
                    is_Diverging = null;
                    break;
                }
            }
        }
        // Colorfulness
        String is_Multicolor = "Multicolor";
        String colors_String = get_Lut_Colors(lut_Name);
        String[] colors_Array = colors_String.split(", ");
        int color_Count = 0;
        for (String color : colors_Array) {
            if (!color.equalsIgnoreCase("black") && !color.equalsIgnoreCase("white") && !color.equalsIgnoreCase("gray")) {
                color_Count++;
            }
        }
        if (color_Count <= 1) is_Multicolor = "Monochrome";
        // Basic LUT
        String[] classic_LUTs = new String[]{"Red", "Green", "Blue", "Cyan", "Magenta", "Yellow", "Grays", "HiLo"};
        String is_Classic = null;
        for(int i = 0; i < classic_LUTs.length; i++) {
            if (classic_LUTs[i].equals(lut_Name)) is_Classic = "Basic";
        }
        // Build Description String
        StringBuilder infos = new StringBuilder();
        if (is_Classic != null)
            infos.append(is_Classic).append(", ");
        infos.append(is_Linear);
        if (is_Diverging != null && is_Isoluminant == null)
            infos.append(", ").append(is_Diverging);
        if (is_Isoluminant != null && lut_Luminance[0] != 0)
            infos.append(", ").append(is_Isoluminant);
        if (is_Cyclic != null)
            infos.append(", ").append(is_Cyclic);
        if (is_Isoluminant == null && is_Cyclic == null && is_Diverging == null)
            infos.append(", ").append((lut_Luminance[255] > lut_Luminance[0]) ? "ascending" : "inverted");
        infos.append(", ").append("min:").append(IJ.pad(lut_Luminance[0],3)).append(", max:").append(IJ.pad(lut_Luminance[255],3)); // Add luminance bounds
        infos.append(", ").append(is_Multicolor);
        return infos.toString();
    }

    private static int[] get_Lutinance(byte[] reds, byte[] greens, byte[] blues) {
        // Returns an array of luminance values of the LUT
        int[] lutinance = new int[256];
        for (int i = 0; i < 256; i++) {
            int[] rgb = {reds[i] & 255, greens[i] & 255, blues[i] & 255};
            lutinance[i] = get_Luminance(rgb);
        }
        return lutinance;
    }

    private static int get_Luminance(int[] rgb) {
        // Returns luminance value of the rgb color
        float[] rgb_Weight = {0.299f, 0.587f, 0.114f};
        float luminance = 0;
        for (int i = 0; i < 3; i++) {
            luminance += (rgb[i] * rgb_Weight[i]);
        }
        return Math.round(luminance);
    }

    private static boolean is_Near(int a, int b, int tolerance) {
        return Math.abs(((256 + a) % 256) - ((256 + b) % 256)) <= tolerance;
    }

    private static DocumentListener get_Filter_Listener(JTextField search_Bar, TableRowSorter<TableModel> table_Sorter) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter_Table();
            }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter_Table();
            }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filter_Table();
            }
            private void filter_Table() {
                String text = search_Bar.getText();
                if (text.trim().isEmpty()) {
                    table_Sorter.setRowFilter(null);
                } else {
                    String[] words = text.trim().split("\\s+");
                    table_Sorter.setRowFilter(new RowFilter<Object, Object>() {
                        @Override
                        public boolean include(Entry<? extends Object, ? extends Object> entry) {
                            for (String word : words) {
                                boolean exclude = word.startsWith("!");
                                String keyword = exclude ? word.substring(1) : word;
                                boolean wordFound = false;
                                for (int column = 0; column < entry.getValueCount(); column++) {
                                    String value = entry.getStringValue(column).toLowerCase();
                                    if (exclude) {
                                        if (value.contains(keyword.toLowerCase())) {
                                            return false;
                                        }
                                    } else {
                                        if (value.contains(keyword.toLowerCase())) {
                                            wordFound = true;
                                            break;
                                        }
                                    }
                                }
                                if (!exclude && !wordFound) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    });
                }
            }
        };
    }

    private static TransferHandler get_Transfer_Handler() {
        return new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                JTable table = (JTable) c;
                int row = table.getSelectedRow();
                if (row != -1) {
                    String lut_Name = table.getValueAt(row, 0).toString();
                    return new StringSelection(lut_Name);
                }
                return null;
            }
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        };
    }

    private static DefaultTableModel get_Table_Model(Object[][] table_Data) {
        return new DefaultTableModel(table_Data, COLUMN_NAMES) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? ImageIcon.class : Object.class;
            }
        };
    }
}