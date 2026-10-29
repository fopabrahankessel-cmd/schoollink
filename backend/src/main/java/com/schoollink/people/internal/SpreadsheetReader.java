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
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Reads an import file into text cells (UC-A3): CSV (separator ";" or ",", UTF-8 or the
 * Windows-1252 that Excel writes) or the first sheet of an .xlsx workbook. Dates in Excel become
 * ISO text (yyyy-MM-dd). The file is read in memory and never stored.
 *
 * <p>Limits are enforced while reading, not after: at most {@link #MAX_ROWS} non-blank data rows
 * and {@link #MAX_COLUMNS} columns, and an .xlsx may not expand to more than {@link #MAX_XLSX_ENTRY}
 * bytes per part, so a small upload cannot exhaust memory (zip bombs, sparse sheets with a huge
 * last row or column).
 */
@Component
class SpreadsheetReader {

	/** One school has at most a few thousand students. */
	static final int MAX_ROWS = 5000;

	/** The template has 14 columns; anything beyond this is ignored. */
	static final int MAX_COLUMNS = 64;

	/** A legitimate 5,000-row sheet is a few MB of XML once decompressed. */
	static final int MAX_XLSX_ENTRY = 20 * 1024 * 1024;

	private static final DataFormatter TEXT = new DataFormatter(Locale.ROOT);

	static {
		// process-wide POI limits; nothing else in the application reads Office files
		ZipSecureFile.setMinInflateRatio(0.02);
		ZipSecureFile.setMaxEntrySize(MAX_XLSX_ENTRY);
		IOUtils.setByteArrayMaxOverride(MAX_XLSX_ENTRY);
	}

	/** @param number the row number as the spreadsheet shows it (the header is row 1) */
	record Line(int number, List<String> cells) {
	}

	/** @param rows the non-blank data rows, in order */
	record Table(List<String> headers, List<Line> rows) {
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
			String[] header = reader.readNext();
			if (header == null) {
				throw ApiException.invalid("import.file_empty", "file");
			}
			Rows rows = new Rows();
			int number = 1;
			for (String[] cells = reader.readNext(); cells != null; cells = reader.readNext()) {
				number++;
				rows.add(number, Arrays.asList(cells).subList(0, Math.min(cells.length, MAX_COLUMNS)));
			}
			return new Table(cap(Arrays.asList(header)), rows.lines);
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
			List<String> header = null;
			Rows rows = new Rows();
			// only the rows that exist in the file, however large the last row number is
			for (Row row : sheet) {
				int width = Math.min(Math.max(row.getLastCellNum(), 0), MAX_COLUMNS);
				List<String> cells = new ArrayList<>(width);
				for (int c = 0; c < width; c++) {
					cells.add(text(row.getCell(c)));
				}
				if (header == null) {
					if (cells.stream().allMatch(String::isBlank)) {
						continue;
					}
					header = cells;
				}
				else {
					rows.add(row.getRowNum() + 1, cells);
				}
			}
			if (header == null) {
				throw ApiException.invalid("import.file_empty", "file");
			}
			return new Table(header, rows.lines);
		}
		catch (IOException | RuntimeException ex) {
			if (ex instanceof ApiException api) {
				throw api;
			}
			// corrupt, encrypted, over the size limits or not really a workbook
			throw ApiException.invalid("import.file_unreadable", "file");
		}
	}

	/** Collects non-blank rows and stops as soon as there are too many. */
	private static final class Rows {

		final List<Line> lines = new ArrayList<>();

		void add(int number, List<String> cells) {
			if (cells.stream().allMatch(String::isBlank)) {
				return;
			}
			if (lines.size() == MAX_ROWS) {
				throw ApiException.invalid("import.too_many_rows", "file", MAX_ROWS);
			}
			lines.add(new Line(number, cells));
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

	private static List<String> cap(List<String> cells) {
		return cells.subList(0, Math.min(cells.size(), MAX_COLUMNS));
	}

	private static long count(String line, char c) {
		return line.chars().filter(ch -> ch == c).count();
	}

}
