package net.fortuna.ical4j.model;

import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.Sequence;

import java.util.Comparator;
import com.google.common.base.Optional;

/**
 * A comparator to determine natural ordering of component instances based on
 * sequence information.
 *
 * See <a href="https://tools.ietf.org/html/rfc5546#section-2.1.5">RFC5446 - Message Sequencing</a>
 * for further details.
 */
public class ComponentSequenceComparator implements Comparator<Component> {

    @Override
    public int compare(Component o1, Component o2) {
        int retVal = 0;

        Sequence defaultSequence = new Sequence(0);
        Sequence sequence1 = Optional.fromNullable((Sequence) o1.getProperty(Property.SEQUENCE)).or(defaultSequence);
        Sequence sequence2 = Optional.fromNullable((Sequence) o2.getProperty(Property.SEQUENCE)).or(defaultSequence);

        retVal = sequence1.compareTo(sequence2);
        if (retVal == 0) {
            DtStamp defaultDtStamp = new DtStamp(new DateTime(0));
            DtStamp dtStamp1 = Optional.fromNullable((DtStamp) o1.getProperty(Property.DTSTAMP)).or(defaultDtStamp);
            DtStamp dtStamp2 = Optional.fromNullable((DtStamp) o2.getProperty(Property.DTSTAMP)).or(defaultDtStamp);

            retVal = dtStamp1.compareTo(dtStamp2);
        }
        return retVal;
    }
}
