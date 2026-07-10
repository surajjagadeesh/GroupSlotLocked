Add-Type -AssemblyName System.Drawing

$srcPath = 'C:\Users\Suraj Jagadeesh\.cursor\projects\c-Users-Suraj-Jagadeesh-IdeaProjects-GroupSlotLocked\assets\c__Users_Suraj_Jagadeesh_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_inventory-af102c19-23dc-47b4-bdbe-9c6697e60cff.png'
$outDir = 'C:\Users\Suraj Jagadeesh\IdeaProjects\GroupSlotLocked\src\main\resources\icons\slots'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$bmp = [System.Drawing.Bitmap]::FromFile($srcPath)
Write-Output "source=$($bmp.Width)x$($bmp.Height)"

function Test-Stone([System.Drawing.Color]$color)
{
	return ($color.R -ge 82 -and $color.R -le 125 -and $color.G -ge 80 -and $color.G -le 115 -and $color.B -ge 60 -and $color.B -le 100)
}

# Fixed crop boxes tuned for the 262x299 equipment panel screenshot.
$slots = @(
	@{ Name = 'head'; X = 104; Y = 4; Size = 48 }
	@{ Name = 'cape'; X = 39; Y = 58; Size = 52 }
	@{ Name = 'neck'; X = 104; Y = 58; Size = 48 }
	@{ Name = 'ammo'; X = 164; Y = 58; Size = 52 }
	@{ Name = 'main_hand'; X = 16; Y = 116; Size = 54 }
	@{ Name = 'body'; X = 104; Y = 116; Size = 48 }
	@{ Name = 'off_hand'; X = 184; Y = 116; Size = 54 }
	@{ Name = 'legs'; X = 104; Y = 183; Size = 48 }
	@{ Name = 'gloves'; X = 16; Y = 233; Size = 54 }
	@{ Name = 'boots'; X = 104; Y = 233; Size = 48 }
	@{ Name = 'ring'; X = 184; Y = 233; Size = 54 }
)

$targetSize = 32

foreach ($slot in $slots)
{
	$rect = New-Object System.Drawing.Rectangle $slot.X, $slot.Y, $slot.Size, $slot.Size
	$crop = $bmp.Clone($rect, $bmp.PixelFormat)

	$scaled = New-Object System.Drawing.Bitmap $targetSize, $targetSize
	$graphics = [System.Drawing.Graphics]::FromImage($scaled)
	$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
	$graphics.DrawImage($crop, 0, 0, $targetSize, $targetSize)
	$graphics.Dispose()
	$crop.Dispose()

	$outPath = Join-Path $outDir ($slot.Name + '.png')
	$scaled.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
	$scaled.Dispose()

	Write-Output ("{0}: ({1},{2}) {3}x{3}" -f $slot.Name, $slot.X, $slot.Y, $slot.Size)
}

$bmp.Dispose()
