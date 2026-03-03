package com.game.hub.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TabularExportSupport {
    private TabularExportSupport() {
    }

    public static byte[] toCsv(String[] headers, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        if (headers != null && headers.length > 0) {
            for (int i = 0; i < headers.length; i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(quote(headers[i]));
            }
            csv.append('\n');
        }

        if (rows != null) {
            for (List<String> row : rows) {
                if (row == null) {
                    csv.append('\n');
                    continue;
                }
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        csv.append(',');
                    }
                    csv.append(quote(row.get(i)));
                }
                csv.append('\n');
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toExcel(String sheetName, String[] headers, List<List<String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String safeSheetName = (sheetName == null || sheetName.isBlank()) ? "Data" : sheetName.trim();
            Sheet sheet = workbook.createSheet(safeSheetName);

            int rowIndex = 0;
            if (headers != null && headers.length > 0) {
                Row headerRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(nullSafe(headers[i]));
                }
            }

            if (rows != null) {
                for (List<String> dataRow : rows) {
                    Row row = sheet.createRow(rowIndex++);
                    if (dataRow == null) {
                        continue;
                    }
                    for (int i = 0; i < dataRow.size(); i++) {
                        row.createCell(i).setCellValue(nullSafe(dataRow.get(i)));
                    }
                }
            }

            int totalColumns = headers == null ? 0 : headers.length;
            if (totalColumns == 0 && rows != null && !rows.isEmpty() && rows.get(0) != null) {
                totalColumns = rows.get(0).size();
            }
            for (int i = 0; i < totalColumns; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot export table to Excel", ex);
        }
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
