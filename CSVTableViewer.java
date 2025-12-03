import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
Get-Process | select id,ProcessName -f 100 | ConvertTo-Csv -delim "`t" -UseQuotes Never | java '-Dsun.java2d.uiScale=4' .\CSVTableViewer.java --in - --delimiter "`t" --column-types numeric,string --pass-thru

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

        // Set column comparators for numeric columns
        for (int i = 0; i < columnTypes.length; i++) {
            if (columnTypes[i].equalsIgnoreCase("double")) {
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

            okButton.addActionListener(e -> {
                int[] selectedRows = table.getSelectedRows();
                for (int row : selectedRows) {
                    System.out.println(table.convertRowIndexToModel(row)); // Print model indices
                }
                System.exit(0);
            });

            cancelButton.addActionListener(e -> System.exit(0));

            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);
        }

        add(panel);

        // Initialize row count
        updateRowCount();
		
		pack();
        setLocationRelativeTo(null);
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
        String delimiter = ",";
        String rowDelimiter = "\r?\n";
        String[] columnTypes;
		String dataStr = readString(params.get("in"));
		
		//if (args.length >= 2) {
        //    delimiter = args[0];
        //} else {
		//	delimiter = ",";
		//}
		//if (params.containsKey("in")) {
		//}
		if (params.containsKey("delimiter")) {
			delimiter = params.get("delimiter");
		}
		if (params.containsKey("column-types")) {
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
		// if () {
			// passThruMode = true;
		// }

        
        //if (args.length >= 3) {
        //    columnTypes = args[1].split(",");
        //} else {
		//	columnTypes = new String[0];
		//}

        SwingUtilities.invokeLater(() -> {
            CSVTableViewer viewer = new CSVTableViewer(data, columnTypes, passThruMode);
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
	
}
