package com.teammatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "com.teammatch", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class TeamMatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamMatchApplication.class, args);
	}

}
