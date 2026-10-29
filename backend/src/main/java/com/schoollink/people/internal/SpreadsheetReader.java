package com.schoollink.people.internal;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.schoollink.shared.error.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Reads an import file into text cells (UC-A3): CSV (separator ";" or ",", UTF-8 or the
 * Windows-1252 that Excel writes) or the first sheet of an .xlsx workbook. Dates in Excel become
 * ISO text (yyyy-MM-dd). The file is read in memory and never stored.
 */
@Component
class SpreadsheetReader {

	private static final DataFormatter TEXT = new DataFormatter(Locale.ROOT);

	record Table(List<String> headers, List<List<String>> rows) {
	}

	Table read(String fileName, byte[] content) {
		String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
		if (name.endsWith(".csv")) {
			return csv(content);
		}
		if (name.endsWith(".xlsx")) {
			return xlsx(content);
		}
		throw ApiException.invalid("import.format_unsupported", "file");
	}

	private static Table csv(byte[] content) {
		String text = decode(content);
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		String firstLine = text.lines().findFirst().orElse("");
		char separator = count(firstLine, ';') >= count(firstLine, ',') ? ';' : ',';
		try (CSVReader reader = new CSVReaderBuilder(new StringReader(text))
			.withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
			.build()) {
			List<String[]> lines = reader.readAll();
			if (lines.isEmpty()) {
				throw ApiException.invalid("import.file_empty", "file");
			}
			return new Table(Arrays.asList(lines.getFirst()),
					lines.stream().skip(1).map(cells -> Arrays.asList(cells)).toList());
		}
		catch (IOException | CsvException ex) {
			throw ApiException.invalid("import.file_unreadable", "file");
		}
	}

	/** UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252 (Excel's "CSV" on Windows). */
	private static String decode(byte[] content) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(content))
				.toString();
		}
		catch (CharacterCodingException ex) {
			return new String(content, Charset.forName("windows-1252"));
		}
	}

	private static Table xlsx(byte[] content) {
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			if (workbook.getNumberOfSheets() == 0) {
				throw ApiException.invalid("import.file_empty", "file");
			}
			Sheet sheet = workbook.getSheetAt(0);
			List<List<String>> lines = new ArrayList<>();
			int width = 0;
			for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum() && r >= 0; r++) {
				Row row = sheet.getRow(r);
				List<String> cells = new ArrayList<>();
				if (row != null) {
					for (int c = 0; c < Math.max(row.getLastCellNum(), width); c++) {
						cells.add(text(row.getCell(c)));
					}
				}
				if (lines.isEmpty()) {
					width = cells.size();
				}
				lines.add(cells);
			}
			if (lines.isEmpty()) {
				throw ApiException.invalid("import.file_empty", "file");
			}
			return new Table(lines.getFirst(), lines.subList(1, lines.size()));
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			// corrupt, encrypted or not really a workbook (POI throws runtime exceptions too)
			throw ApiException.invalid("import.file_unreadable", "file");
		}
	}

	private static String text(Cell cell) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
			return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
		}
		return TEXT.formatCellValue(cell);
	}

	private static long count(String line, char c) {
		return line.chars().filter(ch -> ch == c).count();
	}

}
