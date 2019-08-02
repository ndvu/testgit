package test.com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "test.com")
public class App {

	public static void main(String[] args) {
		System.out.print("master2");
		System.out.print("test2");
		try {
			SpringApplication.run(App.class, args);
		} catch (Exception e) {
			System.out.print(e);
		}
	}

}
