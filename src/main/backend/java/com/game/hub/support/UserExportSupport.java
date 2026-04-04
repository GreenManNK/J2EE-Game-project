package com.game.hub.support;

import com.game.hub.entity.UserAccount;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class UserExportSupport {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UserExportSupport() {
    }

    public static byte[] toCsv(List<UserAccount> users) {
        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Email,DisplayName,Score,WinningStreak,AbusiveContentViolationCount,CommunicationRestrictedUntil,Role,Online,EmailConfirmed,BannedUntil\n");
        for (UserAccount user : users) {
            csv.append(quote(user.getId())).append(',')
                .append(quote(user.getEmail())).append(',')
                .append(quote(user.getDisplayName())).append(',')
                .append(user.getScore()).append(',')
                .append(user.getWinningStreak()).append(',')
                .append(user.getAbusiveContentViolationCount()).append(',')
                .append(quote(formatDateTime(user.getCommunicationRestrictedUntil()))).append(',')
                .append(quote(user.getRole())).append(',')
                .append(user.isOnline()).append(',')
                .append(user.isEmailConfirmed()).append(',')
                .append(quote(formatDateTime(user.getBannedUntil())))
                .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toExcel(List<UserAccount> users) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            String[] headers = {
                "User ID",
                "Email",
                "Display Name",
                "Score",
                "Winning Streak",
                "Abusive Content Violations",
                "Communication Restricted Until",
                "Role",
                "Online",
                "Email Confirmed",
                "Banned Until"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowIndex = 1;
            for (UserAccount user : users) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(safe(user.getId()));
                row.createCell(1).setCellValue(safe(user.getEmail()));
                row.createCell(2).setCellValue(safe(user.getDisplayName()));
                row.createCell(3).setCellValue(user.getScore());
                row.createCell(4).setCellValue(user.getWinningStreak());
                row.createCell(5).setCellValue(user.getAbusiveContentViolationCount());
                row.createCell(6).setCellValue(formatDateTime(user.getCommunicationRestrictedUntil()));
                row.createCell(7).setCellValue(safe(user.getRole()));
                row.createCell(8).setCellValue(user.isOnline() ? "Online" : "Offline");
                row.createCell(9).setCellValue(user.isEmailConfirmed() ? "Yes" : "No");
                row.createCell(10).setCellValue(formatDateTime(user.getBannedUntil()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot build Excel export", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }
}
