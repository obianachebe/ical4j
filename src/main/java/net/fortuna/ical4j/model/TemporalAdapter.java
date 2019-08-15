package net.fortuna.ical4j.model;

import net.fortuna.ical4j.model.parameter.TzId;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.*;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Objects;

/**
 * The iCalendar specification supports multiple representations of date/time values, as outlined
 * below. This class encapsulates a {@link Temporal} value
 * and provides support for all corresponding representations in the specification.
 *
 * The recommended {@link Temporal} implementations for use with iCal4j are as follows:
 *
 * <ul>
 *     <li>{@link LocalDate} - represents an iCalendar DATE value as defined in section 3.3.4 of RFC5545</li>
 *     <li>{@link LocalDateTime} - represents an iCalendar FORM #1: DATE-TIME value as defined in section 3.3.5 of RFC5545</li>
 *     <li>{@link java.time.Instant} - represents an iCalendar FORM #2: DATE-TIME value as defined in section 3.3.5 of RFC5545</li>
 *     <li>{@link ZonedDateTime} - represents an iCalendar FORM #3: DATE-TIME value as defined in section 3.3.5 of RFC5545</li>
 * </ul>
 *
 * Note that a local (i.e. floating) temporal type is used we need to apply a {@link ZoneId} for calculations such as
 * recurrence inclusions and other date-based comparisons. Use {@link TemporalAdapter#isFloating(Temporal)} to determine floating
 * instances.
 *
 * @param <T> A concrete implementation of {@link Temporal}
 */
public class TemporalAdapter<T extends Temporal> implements Serializable {

    /**
     * A formatter capable of parsing to multiple temporal types based on the input string.
     */
    private static CalendarDateFormat PARSE_FORMAT = new CalendarDateFormat(
            "yyyyMMdd['T'HHmmss[X]]", Instant::from, LocalDateTime::from, LocalDate::from);

    private final T temporal;

    public TemporalAdapter(TemporalAdapter<T> adapter) {
        this.temporal = adapter.temporal;
    }

    public TemporalAdapter(T temporal) {
        Objects.requireNonNull(temporal, "temporal");
        this.temporal = temporal;
    }

    /**
     * Support lazy parsing of value string using a zone id to allow full initialisation of
     * {@link java.time.zone.ZoneRulesProvider} instances.
     *
     * @param value a string representation of a floating date/time value
     * @param tzId a zone id to apply to the parsed value
     */
    private TemporalAdapter(String value, TzId tzId) {
        temporal = (T) Proxy.newProxyInstance(ChronoZonedDateTime.class.getClassLoader(),
                new Class[]{ChronoZonedDateTime.class},
                new InvocationHandler() {
                    private ChronoZonedDateTime temporal;

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (temporal == null) {
                            temporal = CalendarDateFormat.FLOATING_DATE_TIME_FORMAT.parse(value, tzId.toZoneId());
                        }
                        return method.invoke(temporal, args);
                    }
                });
    }

    public T getTemporal() {
        return temporal;
    }

    @Override
    public String toString() {
        if (!ChronoUnit.SECONDS.isSupportedBy(temporal)) {
            return toString(CalendarDateFormat.DATE_FORMAT);
        } else {
            if (isFloating(temporal)) {
                return toString(CalendarDateFormat.FLOATING_DATE_TIME_FORMAT);
            } else if (isUtc(temporal)) {
                return toString(CalendarDateFormat.UTC_DATE_TIME_FORMAT);
            } else {
                return toString(CalendarDateFormat.FLOATING_DATE_TIME_FORMAT, ZoneId.systemDefault());
            }
        }
    }

    public String toString(CalendarDateFormat format) {
        return format.format(temporal);
    }

    public String toString(CalendarDateFormat format, ZoneId zoneId) {
        return format.format(temporal, zoneId);
    }

    public ZonedDateTime toLocalTime() {
        return toLocalTime(ZoneId.systemDefault());
    }

    public ZonedDateTime toLocalTime(ZoneId zoneId) {
        if (isFloating(temporal)) {
            return ((LocalDateTime) temporal).atZone(zoneId);
        } else if (isUtc(temporal)) {
            return ((Instant) temporal).atZone(zoneId);
        } else {
            return ZonedDateTime.from(temporal);
        }
    }

    /**
     * Parse a string representation of a temporal value.
     *
     * @param value a string representing a temporal
     * @return an adapter containing the parsed temporal value and format type
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static <T extends Temporal> TemporalAdapter<T> parse(String value) throws DateTimeParseException {
        return new TemporalAdapter<>((T) PARSE_FORMAT.parse(value));
    }

    /**
     * Parse a string representation of a temporal value applicable to the specified timezone.
     *
     * @param value a string representing a floating temporal value
     * @param zoneId a timezone applied to the parsed value
     * @return an adapter containing the parsed temporal value
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static TemporalAdapter<ZonedDateTime> parse(String value, ZoneId zoneId) {
        return new TemporalAdapter<>(CalendarDateFormat.FLOATING_DATE_TIME_FORMAT.parse(value, zoneId));
    }

    /**
     * Parse a string representation of a temporal value applicable to the specified timezone.
     *
     * @param value a string representing a floating temporal value
     * @param tzId a timezone applied to the parsed value
     * @return an adapter containing the parsed temporal value
     * @throws DateTimeParseException if the string cannot be parsed
     */
    public static TemporalAdapter<ZonedDateTime> parse(String value, TzId tzId) {
        return new TemporalAdapter<>(value, tzId);
    }

    /**
     * This method provides support for conversion of legacy {@link Date} and {@link DateTime} instances to temporal
     * values.
     *
     * @param date a date/time instance
     * @return a temporal adapter instance equivalent to the specified date/time value
     */
    @SuppressWarnings("deprecation")
    public static TemporalAdapter from(Date date) {
        Temporal temporal;
        if (date instanceof DateTime) {
            DateTime dateTime = (DateTime) date;
            if (dateTime.isUtc()) {
                temporal = date.toInstant();
            } else {
                temporal = ZonedDateTime.ofInstant(date.toInstant(), dateTime.getTimeZone().toZoneId());
            }
        } else {
            temporal = LocalDate.from(date.toInstant());
        }
        return new TemporalAdapter<>(temporal);
    }

    /**
     * Indicates whether the temporal type represents a "floating" date/time value.
     * @return true if the temporal type is floating, otherwise false
     */
    public static boolean isFloating(Temporal date) {
        return !ChronoField.OFFSET_SECONDS.isSupportedBy(date) &&
                !ChronoField.INSTANT_SECONDS.isSupportedBy(date);
    }

    /**
     * Indicates whether the temporal type represents a UTC date/time value.
     * @return true if the temporal type is in UTC time, otherwise false
     */
    public static boolean isUtc(Temporal date) {
        return !ChronoField.OFFSET_SECONDS.isSupportedBy(date);
    }

    public static <T extends Temporal> boolean isBefore(T date1, T date2) {
        if (date1 instanceof LocalDate && date2 instanceof LocalDate) {
            return ((LocalDate) date1).isBefore((LocalDate) date2);
        } else if (date1 instanceof LocalDateTime) {
            return ((LocalDateTime) date1).isBefore((LocalDateTime) date2);
        }
        return Instant.from(date1).isBefore(Instant.from(date2));
    }

    public static <T extends Temporal> boolean isAfter(T date1, T date2) {
        if (date1 instanceof LocalDate) {
            return ((LocalDate) date1).isAfter((LocalDate) date2);
        } else if (date1 instanceof LocalDateTime) {
            return ((LocalDateTime) date1).isAfter((LocalDateTime) date2);
        }
        return Instant.from(date1).isAfter(Instant.from(date2));
    }
}