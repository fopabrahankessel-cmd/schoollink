package com.schoollink.school.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.school.SchoolClasses;
import com.schoollink.testsupport.TestWorld;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** UC-D9, D36: the block of each class and the blocks' names, within one school (NFR-8). Generated data only. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BlockLookupTests {

	@Autowired
	SchoolClasses classes;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
	}

	@Test
	void blockOfAndBlockNamesStayInTheSchool() {
		UUID blockY = jdbc.queryForObject("SELECT id FROM block WHERE school_id = ?", UUID.class, world.schoolY);

		assertThat(classes.blockOf(world.schoolX, Set.of(world.classA, world.classB, world.classC)))
			.isEqualTo(Map.of(world.classA, world.block1, world.classB, world.block2));
		assertThat(classes.blockNames(world.schoolX, Set.of(world.block1, blockY))).isEqualTo(Map.of(world.block1, "Bloc 1"));
		assertThat(classes.blockOf(world.schoolX, Set.of())).isEmpty();
		assertThat(classes.blockNames(world.schoolX, Set.of())).isEmpty();
	}

}
