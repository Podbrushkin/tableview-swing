function Out-GridViewJava {
	param (
		[Parameter(ValueFromPipeline = $true)]
			$InputObject,
		[switch]$PassThru
	)

	begin {
		[array]$items = @()
	}

	process {
		$items += $InputObject
	}

	end {
		if ($items.count -eq 0) {return}
		$formatData = Get-FormatData -TypeName ([array]$items)[0].GetType()
		$viewDef = $formatData.FormatViewDefinition | ? Control -is [System.Management.Automation.TableControl] | select -f 1

		# This one handles when pwsh has display hint for these objects
		$selectionMap = @()
		for ($i = 0; $i -lt $viewDef.Control.Headers.Label.Count; $i++) {
		  $de = $viewDef.Control.Rows.Columns.DisplayEntry[$i]
		  $isScript = $de.ValueType -eq 'ScriptBlock'
		  $expr = $isScript ? [scriptblock]::Create($de.Value) : $de.Value

		  # Remove ANSI color chars like in gci
		  if ($expr -eq 'NameString') {
			$expr = { ($_.NameString -replace '\u001b\[[0-9;]*m','') }
		  }

		  $label = $viewDef.Control.Headers.Label[$i] ?? $de.Value
		  $selectionMap += @{Name=$label; Expression=$expr; }
		}
		$firstItem = ([array]$items)[0]

		# This one handles String and primitives
		if (!$selectionMap -and ($firstItem -is [ValueType] -or $firstItem -is [string])) {
		  $selectionMap = @(@{Name='Value'; Expression={$_}})
		}

		# This is for all other objects
		if (!$selectionMap) {
		  $selectionMap = $firstItem.psobject.Properties.name
		}

		#$global:selectionMap = $selectionMap
		#$global:items = $items

		$rs = [char]30; $us = [char]31;
		$headersStr = $selectionMap | ForEach-Object { $_.name ?? $_ } | Join-String -Separator "$us"
		$types = New-Object 'object[]' $selectionMap.Count
		$dataStr = $items | Select-Object $selectionMap | ForEach-Object {"$headersStr"}{
			$_.psobject.properties.Value | ForEach-Object {$i=0}{
				if (($null -ne $_) -and ($null -eq $types[$i])) {
					$typeName = $_.getType().Name
					if ($typeName -match 'byte|short|int32|int64|long|sbyte|ushort|uint32|ulong|float|double|decimal') {$types[$i] = 'number'}
					elseif ($_ -match '^https?://') {$types[$i] = 'url'}
				}
				$i++
				$_
			} | Join-String -Separator "$us"
		} | Join-String -Separator "$rs"


		# column without cells will be of type String
		$types = $types | ForEach-Object { if ($null -eq $_) {"string"} else {$_} }
		#Write-Host $types

		# To start jar: java '-Dsun.java2d.uiScale=4' -jar ./tableview-swing.jar --dark-mode
		# To start source: java '-Dsun.java2d.uiScale=4' ./CSVTableViewer.java --dark-mode

		#Write-Host $dataStr
		#$JAVA_TABLE_VIEWER = "$PSScriptRoot/CSVTableViewer.java" # This one for plain source
		#$JAVA_TABLE_VIEWER = "CSVTableViewer"
		$JAVA_TABLE_VIEWER = "$PSScriptRoot/tableview-swing.jar" # This one for jar
		#$JAVA_PATH = 'java'
		$JAVA_PATH = "java" # You can put here full path to /bin/java executable
		#write-host $PSScriptRoot
		$args = @()
		if ($PassThru) {$args += '--pass-thru'}
		$args += '--in','-'
		$args += '--delimiter',"$us"
		$args += '--row-delimiter',"$rs"
		$args += '--column-types',($types -join ',')
		$args += "--dark-mode"
		$argsStr = $args | ForEach-Object {"""$_"""} | Join-String -sep " "

		#$javaArgs = "$JAVA_TABLE_VIEWER $argsStr" 		# This one for plain source
		$javaArgs = "-jar $JAVA_TABLE_VIEWER $argsStr" 	# This one for jar

		# This one makes app 4x bigger
		$javaArgs = '-Dsun.java2d.uiScale=4 '+$javaArgs

		$processStartInfo = New-Object System.Diagnostics.ProcessStartInfo
		$processStartInfo.FileName = $JAVA_PATH
		$processStartInfo.Arguments = $javaArgs
		$processStartInfo.RedirectStandardInput = $true
		$processStartInfo.RedirectStandardError = $true
		if ($PassThru) {
			$processStartInfo.RedirectStandardOutput = $true
		}
		$processStartInfo.UseShellExecute = $false
		$processStartInfo.CreateNoWindow = $true

		$process = [System.Diagnostics.Process]::Start($processStartInfo)
		#return $process
		if ($dataStr) {
			# Write data to the Java process's STDIN
			$streamWriter = $process.StandardInput
			#foreach ($line in $csv) {
				$streamWriter.WriteLine($dataStr)
			#}
			$streamWriter.Close()


			#write-host ($process.StandardOutput.ReadToEnd())
			#$process.WaitForExit()

			if ($PassThru) {
				# Wait for the Java process to exit
				$process.WaitForExit()

				# Read selected row indices from STDOUT
				$selectedIndices = $process.StandardOutput.ReadToEnd() -split "`r?`n" | ForEach-Object {
					if ($_ -match '^\d+$') { [int]$_ } else { $null }
				} | Where-Object { $_ -ne $null }

				# Return the selected objects
				if ($selectedIndices.count -gt 0) {
				 $items[$selectedIndices]
				}
			}

		}
	}
}
