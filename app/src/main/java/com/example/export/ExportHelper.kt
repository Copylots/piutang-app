package com.example.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.database.PiutangEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    private fun fRp(n: Int): String {
        return "Rp %,d".format(n).replace(",", ".")
    }

    fun exportToCsv(context: Context, dataList: List<PiutangEntity>): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(downloadsDir, "SinarMas_Laporan_$timestamp.csv")
            val writer = FileOutputStream(file).bufferedWriter()

            writer.write("No,Nama Meubel,0 Bln,1 Bln,2 Bln,3 Bln,Total\n")
            dataList.forEachIndexed { index, item ->
                writer.write("${index + 1},\"${item.nama}\",${item.m0},${item.m1},${item.m2},${item.m3},${item.total}\n")
            }
            
            // Add sums row
            val s0 = dataList.sumOf { it.m0 }
            val s1 = dataList.sumOf { it.m1 }
            val s2 = dataList.sumOf { it.m2 }
            val s3 = dataList.sumOf { it.m3 }
            val sT = dataList.sumOf { it.total }
            writer.write("TOTAL,TOTAL KESELURUHAN,$s0,$s1,$s2,$s3,$sT\n")
            
            writer.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToPdf(context: Context, dataList: List<PiutangEntity>, logoPath: String): File? {
        val document = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        
        val paint = Paint()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(downloadsDir, "SinarMas_Laporan_$timestamp.pdf")

            val rowsPerPage = 22
            val totalRows = dataList.size
            val totalPages = Math.max(1, (totalRows + rowsPerPage - 1) / rowsPerPage)

            for (pageNumber in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                var currentY = 40f

                // Header - Page 1 only
                if (pageNumber == 0) {
                    // Logo loading
                    if (logoPath.isNotEmpty()) {
                        val logoFile = File(logoPath)
                        if (logoFile.exists()) {
                            try {
                                val originalBitmap = BitmapFactory.decodeFile(logoPath)
                                if (originalBitmap != null) {
                                    // Scale logo to w=40, h=40
                                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 45, 45, true)
                                    canvas.drawBitmap(scaledBitmap, 40f, currentY, paint)
                                }
                            } catch (logoEx: Exception) {
                                logoEx.printStackTrace()
                            }
                        }
                    }

                    // Company Titles
                    canvas.drawText("APLIKASI SINAR MAS ALUMINIUM", 100f, currentY + 15f, headerPaint)
                    textPaint.textSize = 8f
                    textPaint.color = Color.DKGRAY
                    canvas.drawText("Laporan Pembukuan Piutang Meubel & Toko", 100f, currentY + 30f, textPaint)
                    val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                    canvas.drawText("Dicetak pada: $currentDate", 100f, currentY + 42f, textPaint)

                    currentY += 60f

                    // Divider line
                    paint.color = Color.rgb(0, 76, 120) // Deep blue divider
                    paint.strokeWidth = 2f
                    canvas.drawLine(40f, currentY, (pageWidth - 40).toFloat(), currentY, paint)

                    currentY += 20f
                } else {
                    // Sub-Header for following pages
                    canvas.drawText("SINAR MAS ALUMINIUM - Halaman ${pageNumber + 1}", 40f, currentY + 15f, headerPaint)
                    currentY += 35f
                }

                // Draw Table Headers
                paint.color = Color.rgb(230, 235, 240) // Table header background light blue
                paint.style = Paint.Style.FILL
                canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, paint)

                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, paint)

                textPaint.textSize = 8f
                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                val colWidths = intArrayOf(25, 150, 60, 60, 60, 60, 60)
                val headers = arrayOf("No", "Nama Meubel", "0 Bln", "1 Bln", "2 Bln", "3 Bln", "Total")

                var colX = 40f
                for (i in headers.indices) {
                    canvas.drawText(headers[i], colX + 4f, currentY + 13f, textPaint)
                    colX += colWidths[i]
                    if (i < headers.size - 1) {
                        canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                    }
                }

                currentY += 20f
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                // Fill Table rows
                val startIndex = pageNumber * rowsPerPage
                val endIndex = Math.min(startIndex + rowsPerPage, totalRows)

                for (idx in startIndex until endIndex) {
                    val item = dataList[idx]

                    // Zebra stripe backgrounds
                    if (idx % 2 == 1) {
                        paint.color = Color.rgb(245, 247, 250)
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 18f, paint)
                    }

                    // Outer border
                    paint.color = Color.BLACK
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 18f, paint)

                    // Columns
                    colX = 40f
                    // No
                    canvas.drawText((idx + 1).toString(), colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[0], currentY, colX + colWidths[0], currentY + 18f, paint)
                    colX += colWidths[0]

                    // Nama
                    val truncatedNama = if (item.nama.length > 28) item.nama.take(25) + "..." else item.nama
                    canvas.drawText(truncatedNama, colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[1], currentY, colX + colWidths[1], currentY + 18f, paint)
                    colX += colWidths[1]

                    // m0
                    canvas.drawText(fRp(item.m0), colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[2], currentY, colX + colWidths[2], currentY + 18f, paint)
                    colX += colWidths[2]

                    // m1
                    canvas.drawText(fRp(item.m1), colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[3], currentY, colX + colWidths[3], currentY + 18f, paint)
                    colX += colWidths[3]

                    // m2
                    canvas.drawText(fRp(item.m2), colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[4], currentY, colX + colWidths[4], currentY + 18f, paint)
                    colX += colWidths[4]

                    // m3
                    canvas.drawText(fRp(item.m3), colX + 4f, currentY + 12f, textPaint)
                    canvas.drawLine(colX + colWidths[5], currentY, colX + colWidths[5], currentY + 18f, paint)
                    colX += colWidths[5]

                    // total
                    canvas.drawText(fRp(item.total), colX + 4f, currentY + 12f, textPaint)

                    currentY += 18f
                }

                // If last page, draw the grand totals!
                if (pageNumber == totalPages - 1) {
                    paint.color = Color.rgb(220, 230, 242)
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, paint)

                    paint.color = Color.BLACK
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 20f, paint)

                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("TOTAL KESELURUHAN", 45f, currentY + 13f, textPaint)

                    val s0 = dataList.sumOf { it.m0 }
                    val s1 = dataList.sumOf { it.m1 }
                    val s2 = dataList.sumOf { it.m2 }
                    val s3 = dataList.sumOf { it.m3 }
                    val sT = dataList.sumOf { it.total }

                    colX = 40f + colWidths[0] + colWidths[1]
                    // s0
                    canvas.drawText(fRp(s0), colX + 4f, currentY + 13f, textPaint)
                    canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                    colX += colWidths[2]

                    // s1
                    canvas.drawText(fRp(s1), colX + 4f, currentY + 13f, textPaint)
                    canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                    colX += colWidths[3]

                    // s2
                    canvas.drawText(fRp(s2), colX + 4f, currentY + 13f, textPaint)
                    canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                    colX += colWidths[4]

                    // s3
                    canvas.drawText(fRp(s3), colX + 4f, currentY + 13f, textPaint)
                    canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                    colX += colWidths[5]

                    // sT
                    canvas.drawText(fRp(sT), colX + 4f, currentY + 13f, textPaint)
                    canvas.drawLine(colX, currentY, colX, currentY + 20f, paint)
                }

                document.finishPage(page)
            }

            val fos = FileOutputStream(file)
            document.writeTo(fos)
            fos.close()
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }
}
