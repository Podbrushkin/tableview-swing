import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.Reader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
/*
java CSVTableViewer.java
Get-Process | select id,ProcessName -f 100 | ConvertTo-Csv -delim "`t" -UseQuotes Never | java '-Dsun.java2d.uiScale=4' .\CSVTableViewer.java --in - --delimiter "`t" --column-types number,string --pass-thru
Get-Process | select @{n='url';e={'https://duckduckgo.com/?q='+[System.Net.WebUtility]::UrlEncode($_.ProcessName)}},id,ProcessName | ConvertTo-Csv -delim "`t" -UseQuotes Never | java '-Dsun.java2d.uiScale=4' ./CSVTableViewer.java --in - --delimiter "`t" --column-types url,number,string --pass-thru
java '-Dsun.java2d.uiScale=2.5' $javaSwingTableViewer .\pplDepicted.tsv "`t" url
*/
public class CSVTableViewer extends JFrame {
    private JTable table;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel rowCountLabel;
	private static boolean autoFilterMode = true;
	private static DocumentListener autoFilterListener;

    public CSVTableViewer(List<String[]> data, String[] columnTypes, boolean passThruMode) {
        setTitle("CSV/TSV Viewer");
        //setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setEscapeKeyBinding();
        setSearchKeyBinding();

        

        

        // Create table model
        String[] columns = data.get(0); // First row as headers
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        for (int i = 1; i < data.size(); i++) {
            tableModel.addRow(data.get(i));
        }

        // Create table and sorter
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        // stop jtable from reacting to tab
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume(); // Prevent default behavior
                    table.transferFocus(); // Move focus to next component
                }
            }
        });

        // Set column comparators for numeric columns
        for (int i = 0; i < columnTypes.length; i++) {
            if (columnTypes[i].equalsIgnoreCase("number")) {
                sorter.setComparator(i, Comparator.comparingDouble(o -> {
					String val = o.toString();
					try {
						return Double.parseDouble(val);
					} catch (Exception e) {
						return Double.NaN;
					}
				}
				));
            }
        }

        // Add mouse listener to column headers for sorting
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
				if (e.getButton() == 3) { //RMB
					sorter.setSortKeys(null);
				}
                //int column = table.columnAtPoint(e.getPoint());
                //if (e.getClickCount() == 3) {
                //    sorter.setSortKeys(null); // Reset sorting on third click
                //} else {
                //    sorter.sort();
                //}
                //updateRowCount();
            }
        });

        // Add mouse listener to cells for URL handling
		if (Arrays.asList(columnTypes).contains("url")) {
			table.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					int row = table.rowAtPoint(e.getPoint());
					int col = table.columnAtPoint(e.getPoint());
					if (col >= 0 && row >= 0 && columnTypes[col].equalsIgnoreCase("url")) {
						String url = table.getValueAt(row, col).toString().replace(" ","_");
						try {
							Desktop.getDesktop().browse(new URI(url));
						} catch (Exception ex) {
							JOptionPane.showMessageDialog(CSVTableViewer.this, "Could not open URL: " + url, "Error", JOptionPane.ERROR_MESSAGE);
						}
					}
				}
			});
		}

        // Add search field
        searchField = new JTextField(20);
		setupAutoFilterMode(searchField);
        //searchField.addActionListener(new ActionListener() {
        //    @Override
        //    public void actionPerformed(ActionEvent e) {
        //        String text = searchField.getText();
        //        if (text.trim().length() == 0) {
        //            sorter.setRowFilter(null);
        //        } else {
		//			// case-insensitive, unicode support:
        //            sorter.setRowFilter(RowFilter.regexFilter("(?iu)" + text));
        //        }
        //        updateRowCount();
        //    }
        //});

        // Add row count label
        rowCountLabel = new JLabel("Rows: 0");
		
		JButton filterModeButton = new JButton("\u23e9"); //Filter Mode
		filterModeButton.setToolTipText("Quick filtering mode");
        filterModeButton.addActionListener(e -> toggleFilterMode(searchField, filterModeButton));
		filterModeButton.setContentAreaFilled(false);
        filterModeButton.setFocusable(false);
		

        // Layout
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel searchPanel = new JPanel();
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(rowCountLabel);
        searchPanel.add(filterModeButton);
        panel.add(searchPanel, BorderLayout.NORTH);
		
		
		// final JButton selectionModeButton = new JButton("Row Selection");
		final JButton selectionModeButton = new JButton("\u2582");
		selectionModeButton.setToolTipText("Row selection mode");
        selectionModeButton.setFocusable(false);
		searchPanel.add(selectionModeButton);
		
		// Make button less eye-catching
		//selectionModeButton.setBorderPainted(false); // Remove border
		selectionModeButton.setContentAreaFilled(false); // Remove background fill
		//selectionModeButton.setFocusPainted(false); // Remove focus border
		//Dimension prefSize = selectionModeButton.getPreferredSize();
		//selectionModeButton.setPreferredSize(new Dimension ((int)prefSize.getWidth(), (int)(prefSize.getHeight()*0.9)));
		
		selectionModeButton.addActionListener(e -> {
			// Determine the current selection mode and cycle to the next one
			if (table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed()) {
				// Current mode: Row selection -> Switch to cell selection
				table.setRowSelectionAllowed(false);
				table.setColumnSelectionAllowed(false);
				table.setCellSelectionEnabled(true);
				// selectionModeButton.setText("Cell Selection");
				selectionModeButton.setText("\u2598");
				selectionModeButton.setToolTipText("Cell selection mode");
				//System.out.println("Switched to Cell Selection Mode");
			} else if (table.getCellSelectionEnabled()) {
				// Current mode: Cell selection -> Switch to column selection
				table.setCellSelectionEnabled(false);
				table.setColumnSelectionAllowed(true);
				table.setRowSelectionAllowed(false);
				
				selectionModeButton.setText("\u258c");
				selectionModeButton.setToolTipText("Column selection mode");
			} else if (table.getColumnSelectionAllowed()) {
				// Current mode: Column selection -> Switch back to row selection
				table.setColumnSelectionAllowed(false);
				table.setRowSelectionAllowed(true);
				selectionModeButton.setText("\u2582");
				selectionModeButton.setToolTipText("Row selection mode");
			}
		});
		
		// Add OK and Cancel buttons if in PassThru mode
        if (passThruMode) {
            JPanel buttonPanel = new JPanel();
            JButton okButton = new JButton("OK");
            JButton cancelButton = new JButton("Cancel");
            okButton.addActionListener(e -> printSelectedIndicesAndExit());

            cancelButton.addActionListener(e -> System.exit(0));
            
            setEnterSubmitKeyBinding();

            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);
        }

        add(panel);

        // Initialize row count
        updateRowCount();
		
		pack();
        setLocationRelativeTo(null);

        table.requestFocus();
    }

    private void setEscapeKeyBinding() {
        KeyStroke escapeKey = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(escapeKey, "exitApplication");
        
        getRootPane().getActionMap().put("exitApplication", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
    }
    private void setSearchKeyBinding() {
        // Ctrl+F
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
    
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(key, "focusSearch");
        
        getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.requestFocus();
            }
        });
    }
    private void setEnterSubmitKeyBinding() {
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(key, "submitAndExit");

        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submitAndExit");
        table.getActionMap().put("submitAndExit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                printSelectedIndicesAndExit();
            }
        });
        
        getRootPane().getActionMap().put("submitAndExit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println(e);
                printSelectedIndicesAndExit();
            }
        });
    }

    private void printSelectedIndicesAndExit() {
        int[] selectedRows = table.getSelectedRows();
        for (int row : selectedRows) {
            System.out.println(table.convertRowIndexToModel(row)); // Print model indices
        }
        System.exit(0);
    }

    private void updateRowCount() {
        int rowCount = table.getRowCount();
        rowCountLabel.setText("Rows: " + rowCount);
    }
	
	private void toggleFilterMode(JTextField searchField, JButton button) {
        autoFilterMode = !autoFilterMode; // Toggle the mode

        // Update the button text
        if (autoFilterMode) {
            button.setText("\u23e9"); //"Auto Filter"
			button.setToolTipText("Quick filtering mode");
            setupAutoFilterMode(searchField); // Enable auto-filter mode
        } else {
            button.setText("\u23ce");
			button.setToolTipText("<Enter> filtering mode");
            setupManualFilterMode(searchField); // Enable manual-filter mode
        }
		updateRowCount();
    }

    private void setupAutoFilterMode(JTextField searchField) {
        // Remove the ActionListener (if any)
        for (ActionListener listener : searchField.getActionListeners()) {
            searchField.removeActionListener(listener);
        }

        // Add a DocumentListener to apply the filter automatically
        autoFilterListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter(searchField.getText());
            }
        };
		searchField.getDocument().addDocumentListener(autoFilterListener);
    }

    private void setupManualFilterMode(JTextField searchField) {
        // Remove the DocumentListener (if any)
        if (autoFilterListener != null) {
            searchField.getDocument().removeDocumentListener(autoFilterListener);
        }

        // Add an ActionListener to apply the filter only when Enter is pressed
        searchField.addActionListener(e -> applyFilter(searchField.getText()));
    }

    private void applyFilter(String text) {
        if (text.trim().isEmpty()) {
            sorter.setRowFilter(null); // Clear the filter if the text is empty
        } else {
            // Apply a case-insensitive, Unicode-aware regex filter
            sorter.setRowFilter(RowFilter.regexFilter("(?iu)" + text));
        }
    }

    public static void main(String[] args) throws IOException {
		var params = parseArgs(args);
        String delimiter = params.containsKey("delimiter") ? params.get("delimiter") : ",";
        String rowDelimiter = params.containsKey("row-delimiter") ? params.get("row-delimiter") : "\r?\n";
        
        String[] columnTypes;
		String dataStr = params.containsKey("in") ? readString(params.get("in")) : String.join("\n",
            "clmn,also column","somebody,1","once,9","told me,20");
		
        // if (!params.containsKey("in")) columnTypes = new String[]{"string","number"};
		if (!params.containsKey("in")) columnTypes = new String[]{"string","number"};
        else if (params.containsKey("column-types")) {
			columnTypes = params.get("column-types").split(",");
		} 
		else columnTypes = new String[0];
		
		List<String[]> data = new ArrayList<>();
		String[] rows = dataStr.split(rowDelimiter);
		for (var row : rows) {
			data.add(row.split(delimiter));
		}
		rows = null; dataStr = null;
		
		final boolean passThruMode = params.containsKey("pass-thru");

        
        if (params.containsKey("look-and-feel")) setLookAndFeel(params.get("look-and-feel"));
        SwingUtilities.invokeLater(() -> {
            CSVTableViewer viewer = new CSVTableViewer(data, columnTypes, passThruMode);
            if (params.containsKey("dark-mode")) viewer.applySimpleDarkMode();
            viewer.setVisible(true);
        });
    }
	
	// Will read a file or stdin(-) to String
	public static String readString(String path) throws IOException {
		try (Reader reader = path.equals("-") ? 
			  new InputStreamReader(System.in, StandardCharsets.UTF_8) : 
			  new FileReader(path, StandardCharsets.UTF_8)) {
			
			var br = new BufferedReader(reader);
			return br.lines().collect(Collectors.joining(System.lineSeparator()));
		}
	}
	
	public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            boolean isNamed = args[i].startsWith("--");
            var values = List.of(args).stream().skip(i+1).takeWhile(v -> !v.startsWith("--")).collect(Collectors.toList());
            
            // Common <--key value1...>
            if (isNamed && values.size() >= 1) {
                result.put(args[i].substring(2), String.join(";", values));
                i += values.size(); continue;
            }
            // Switch <--key>
            else if (isNamed && values.size() == 0)
                result.put(args[i].substring(2), ""); // switch true
            else System.err.println("What is this arg? "+args[i]);
        }
        return result;
    }

    private void applySimpleDarkMode() {
        // Set frame background
        getContentPane().setBackground(Color.DARK_GRAY);

        UIManager.put("Panel.background", Color.DARK_GRAY);
        UIManager.put("Button.background", new Color(80, 80, 80));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("Viewport.background", Color.DARK_GRAY);
        UIManager.put("TextField.background", Color.DARK_GRAY);
        UIManager.put("TextField.foreground", Color.WHITE);
        // UIManager.put("ScrollBar.background", Color.RED); // bad
        UIManager.put("ScrollBar.thumb", Color.DARK_GRAY);
        // UIManager.put("Table.alternateRowColor", Color.RED);
        // UIManager.put("Table.rowColor", Color.RED); // not compat with bg color
        UIManager.put("Table.background", Color.DARK_GRAY);
        UIManager.put("Table.foreground", Color.WHITE);


        SwingUtilities.updateComponentTreeUI(this);
        
        if (table != null) {
            // table.setRowHeight(64);
            // table.setBackground(new Color(60, 63, 65));
            // table.setForeground(Color.WHITE);
            // table.setGridColor(Color.GRAY);
            // table.setSelectionBackground(new Color(0, 100, 200));
            // table.setSelectionForeground(Color.WHITE);
            
            
            // Header
            JTableHeader header = table.getTableHeader();
            header.setBackground(new Color(43, 43, 43));
            header.setForeground(Color.WHITE);
        }

        
    }

    private static void setLookAndFeel(String name) {
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        
        boolean set = false;
        for (UIManager.LookAndFeelInfo laf : lafs) {
            try {
                if (laf.getName().equalsIgnoreCase(name)) {
                    UIManager.setLookAndFeel(laf.getClassName());
                    set = true;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (!set) {
            for (UIManager.LookAndFeelInfo laf : lafs) {
                System.out.println("Name: " + laf.getName());
                System.out.println("Class: " + laf.getClassName());
                if (UIManager.getLookAndFeel().getName().equals(laf.getName())) System.out.println("Current");
                System.out.println("---");
            }
        }
    }
	
}
