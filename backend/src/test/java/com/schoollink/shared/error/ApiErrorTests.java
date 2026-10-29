package com.schoollink.shared.error;

import com.schoollink.TestcontainersConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One error shape {code, message, field} with the message in the request's language (SPEC §9, NFR-12). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ApiErrorTests.ErrorTestController.class })
class ApiErrorTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void validationErrorInFrench() throws Exception {
		postBody("{\"name\": \"\", \"code\": \"AB\"}", "fr")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.not_blank"))
			.andExpect(jsonPath("$.field").value("name"))
			.andExpect(jsonPath("$.message").value("Ce champ est obligatoire."))
			.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void validationErrorInEnglish() throws Exception {
		postBody("{\"name\": \"\", \"code\": \"AB\"}", "en-GB,en;q=0.9")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.not_blank"))
			.andExpect(jsonPath("$.message").value("This field is required."));
	}

	@Test
	void missingOrUnsupportedLanguageFallsBackToFrench() throws Exception {
		postBody("{\"name\": \"\", \"code\": \"AB\"}", null)
			.andExpect(jsonPath("$.message").value("Ce champ est obligatoire."));
		postBody("{\"name\": \"\", \"code\": \"AB\"}", "de-DE")
			.andExpect(jsonPath("$.message").value("Ce champ est obligatoire."));
	}

	@Test
	void constraintAttributesAppearInTheMessage() throws Exception {
		postBody("{\"name\": \"Paul\", \"code\": \"TOOLONG\"}", "en")
			.andExpect(jsonPath("$.code").value("validation.size"))
			.andExpect(jsonPath("$.field").value("code"))
			.andExpect(jsonPath("$.message").value("Length must be between 2 and 5 characters."));
	}

	@Test
	void severalInvalidFieldsAreListedInDetails() throws Exception {
		postBody("{\"name\": \"\", \"code\": \"X\"}", "fr")
			.andExpect(jsonPath("$.details", hasSize(2)))
			.andExpect(jsonPath("$.details[?(@.field == 'code')].message")
				.value("La longueur doit être comprise entre 2 et 5 caractères."));
	}

	@Test
	void businessErrorIsLocalized() throws Exception {
		mockMvc.perform(get("/__test/errors/not-found").header("Accept-Language", "fr"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"))
			.andExpect(jsonPath("$.message").value("La ressource demandée est introuvable."));
	}

	@Test
	void malformedJsonIsLocalized() throws Exception {
		postBody("{not json", "en")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("request.malformed"))
			.andExpect(jsonPath("$.message").value("The request is malformed."));
	}

	@Test
	void unexpectedErrorLeaksNothing() throws Exception {
		mockMvc.perform(get("/__test/errors/boom").header("Accept-Language", "fr"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("internal"))
			.andExpect(jsonPath("$.message").value("Une erreur inattendue s'est produite. Veuillez réessayer."));
	}

	private ResultActions postBody(String json, String acceptLanguage) throws Exception {
		var request = post("/__test/errors/validate").contentType(MediaType.APPLICATION_JSON).content(json);
		if (acceptLanguage != null) {
			request.header("Accept-Language", acceptLanguage);
		}
		return mockMvc.perform(request);
	}

	record Body(@NotBlank String name, @Size(min = 2, max = 5) String code) {
	}

	@RestController
	static class ErrorTestController {

		@PostMapping("/__test/errors/validate")
		void validate(@Valid @RequestBody Body body) {
		}

		@GetMapping("/__test/errors/not-found")
		void notFound() {
			throw ApiException.notFound("not_found");
		}

		@GetMapping("/__test/errors/boom")
		void boom() {
			throw new IllegalStateException("secret internal detail");
		}

	}

}
