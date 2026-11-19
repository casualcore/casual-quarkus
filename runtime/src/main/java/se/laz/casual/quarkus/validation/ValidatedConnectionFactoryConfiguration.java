package se.laz.casual.quarkus.validation;

/**
 * Contains validated managed connection factory configuration.
 *
 * @param host outbound host
 * @param port outbound port
 * @param poolName network connection pool name
 * @param poolSize network connection pool size, or {@code null} when omitted for reverse outbound
 */
public record ValidatedConnectionFactoryConfiguration(String host, int port, String poolName, Integer poolSize)
{}
