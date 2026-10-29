package com.schoollink.people.internal;

import com.schoollink.shared.error.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-A3 import file limits: enforced while reading, so a small upload cannot exhaust memory. */
class SpreadsheetReaderTests {

	private final SpreadsheetReader reader = new SpreadsheetReader();

	@Test
	void csvStopsAtTheRowLimitWhileReading() {
		StringBuilder csv = new StringBuilder("matricule;nom\n");
		for (int i = 0; i <= SpreadsheetReader.MAX_ROWS; i++) {
			csv.append("TW-").append(i).append(";Nom\n");
		}

		assertRejected(() -> reader.read("eleves.csv", csv.toString().getBytes(StandardCharsets.UTF_8)),
				"import.too_many_rows");
	}

	@Test
	void blankRowsDoNotCountAndKeepTheRowNumbers() {
		StringBuilder csv = new StringBuilder("matricule;nom\n");
		for (int i = 0; i < SpreadsheetReader.MAX_ROWS; i++) {
			csv.append("TW-").append(i).append(";Nom\n;\n");
		}

		var table = reader.read("eleves.csv", csv.toString().getBytes(StandardCharsets.UTF_8));

		assertThat(table.rows()).hasSize(SpreadsheetReader.MAX_ROWS);
		assertThat(table.rows().get(1).number()).isEqualTo(4);
	}

	@Test
	void xlsxStopsAtTheRowLimitWhileReading() throws IOException {
		byte[] xlsx = workbook(sheet -> {
			sheet.createRow(0).createCell(0).setCellValue("matricule");
			for (int r = 1; r <= SpreadsheetReader.MAX_ROWS + 1; r++) {
				sheet.createRow(r).createCell(0).setCellValue("TW-" + r);
			}
		});

		assertRejected(() -> reader.read("eleves.xlsx", xlsx), "import.too_many_rows");
	}

	@Test
	void aSparseSheetWithAFarAwayCellIsReadCheaply() throws IOException {
		byte[] xlsx = workbook(sheet -> {
			var header = sheet.createRow(0);
			header.createCell(0).setCellValue("matricule");
			// the last possible column and row of a sheet
			header.createCell(16_383).setCellValue("far");
			sheet.createRow(1).createCell(0).setCellValue("TW-1");
			sheet.createRow(1_048_575).createCell(0).setCellValue("TW-2");
		});

		var table = reader.read("eleves.xlsx", xlsx);

		assertThat(table.headers()).hasSize(SpreadsheetReader.MAX_COLUMNS);
		assertThat(table.rows()).extracting(SpreadsheetReader.Line::number).containsExactly(2, 1_048_576);
	}

	@Test
	void aWorkbookThatExpandsTooMuchIsRejected() throws IOException {
		// about 30 MB of text once decompressed, well under 1 MB as a file
		byte[] xlsx = workbook(sheet -> {
			String filler = "a".repeat(1000);
			for (int r = 0; r < 1000; r++) {
				var row = sheet.createRow(r);
				for (int c = 0; c < 30; c++) {
					row.createCell(c).setCellValue(filler + r + "-" + c);
				}
			}
		});
		assertThat(xlsx.length).isLessThan(5 * 1024 * 1024);

		assertRejected(() -> reader.read("eleves.xlsx", xlsx), "import.file_unreadable");
	}

	private static byte[] workbook(Consumer<Sheet> fill) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			fill.accept(workbook.createSheet());
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static void assertRejected(Runnable read, String code) {
		assertThatThrownBy(read::run).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
	}

}
