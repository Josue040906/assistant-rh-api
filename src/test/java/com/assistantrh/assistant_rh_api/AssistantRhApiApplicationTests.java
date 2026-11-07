package com.assistantrh.assistant_rh_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AssistantRhApiApplicationTests {

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void lesDatesSqlSontEcritesSansHeureParLApi() {
		assertEquals(
				"{\"date_embauche\":\"2012-08-13\"}",
				jsonMapper.writeValueAsString(Map.of(
						"date_embauche", Date.valueOf(LocalDate.of(2012, 8, 13))
				))
		);
	}

}
