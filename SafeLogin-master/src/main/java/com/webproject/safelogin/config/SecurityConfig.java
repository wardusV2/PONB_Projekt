package com.webproject.safelogin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.messaging.simp.SimpMessageType;
import java.util.Arrays;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

        @Autowired
        private UserDetailsService userDetailsService;

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .ignoringRequestMatchers("/ws/**", "/ws", "/ws/info", "/ws/websocket","/api/history/**"))
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .authorizeHttpRequests(request -> request
                            .requestMatchers(
                                    "/login",
                                    "/register",
                                    "/logout",
                                    "/csrf-token",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/getVideo/**",
                                    "/2fa/verify",
                                    "/ws/**", // WebSocket endpoint
                                    "/ws", // Główny endpoint
                                    "/ws/info", // SockJS info endpoint
                                    "/ws/websocket", // WebSocket transport
                                    "/ws/xhr_streaming", // XHR streaming transport
                                    "/ws/xhr_send", // XHR send transport
                                    "/ws/xhr", // XHR transport
                                    "/ws/jsonp", // JSONP transport
                                    "/ws/jsonp_send", // JSONP send transport
                                    "/ws/eventsource", // EventSource transport
                                    "/ws/htmlfile", // HtmlFile transport
                                    "/sockjs-node/**" // SockJS może używać tego
                            ).permitAll()
                            .anyRequest().authenticated()
                    )
                    .logout(logout -> logout
                            .logoutUrl("/logout")
                            .logoutSuccessHandler((request, response, authentication) -> {
                                response.setStatus(200); // HTTP 200 OK
                            })
                            .invalidateHttpSession(true)
                            .clearAuthentication(true)
                            .deleteCookies("JSESSIONID")
                            .permitAll()
                    )
                    .formLogin().disable()
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
                    .build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173"));
            configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN"));
            configuration.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        public AuthenticationProvider authenticationProvider(){
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
            provider.setPasswordEncoder(new BCryptPasswordEncoder(10));
            provider.setUserDetailsService(userDetailsService);
            return provider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception{
            return config.getAuthenticationManager();
        }


    }

