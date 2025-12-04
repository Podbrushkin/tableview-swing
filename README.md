Simple GUI table to view CSV/TSV data. Written with Java Swing. Powershell's Out-GridView alternative, cross-platform. 


![gif usage pwsh function](https://github.com/user-attachments/assets/f3c5c0e0-5d29-4f6a-82ee-646d73671096)

Usage of java app:
```powershell
# Run java file without creating .class or .jar:
java ./CSVTableViewer.java

# Or, create a jar and run it (faster startup), with increased scaling:
javac ./CSVTableViewer.java
jar --create --main-class CSVTableViewer --file tableview-swing.jar *.class
java '-Dsun.java2d.uiScale=2' -jar ./tableview-swing.jar --dark-mode

# Delete .class files
rm *.class
```

Usage of Powershell function:
```powershell
. ./Out-GridViewJava.ps1
Get-ChildItem | Out-GridViewJava -PassThru
```

```
--in <path>                # Path to file or '-' to read from stdin.
--delimiter <regex>        # Cell delimiter, default: ','.
--row-delimiter <regex>    # Row delimiter, default: '\r?\n' (EOL).
--column-types <arr>       # Types of columns, e.g.: 'string,number,url'. Default: all string.
--dark-mode                # Use dark mode.
--look-and-feel <name>     # Use this Swing look and feel, run with any value to print all available.
--pass-thru                # Show Ok button, when pressed - prints sequence numbers of selected rows and exits.
--help                     # Print this message and exit.
```
