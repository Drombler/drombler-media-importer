/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.drombler.media.importing;

import java.util.Comparator;
import org.drombler.event.core.AllDayEventDuration;
import org.drombler.event.core.EventDuration;

/**
 *
 * @author Florian
 */
public class ImportEventDurationComparator implements Comparator<EventDuration> {

    private static final int LESS = -1;
    private static final int GREATER = 1;
    private static final int EQUAL = 0;

    private final Comparator<AllDayEventDuration> allDayEventDurationComparator
            = Comparator.comparing(AllDayEventDuration::getStartDateInclusive)
                    .thenComparing(AllDayEventDuration::getEndDateInclusive);

    @Override
    public int compare(EventDuration ed1, EventDuration ed2) {
        if (!isAllDayEventDuration(ed1) && isAllDayEventDuration(ed2)) {
            return LESS;
        } else if (isAllDayEventDuration(ed1) && !isAllDayEventDuration(ed2)) {
            return GREATER;
        } else if (isAllDayEventDuration(ed1) && isAllDayEventDuration(ed2)) {
            return allDayEventDurationComparator.compare((AllDayEventDuration) ed1,
                    (AllDayEventDuration) ed2);
        } else {
            return EQUAL;
        }
    }

    private static boolean isAllDayEventDuration(EventDuration eventDuration) {
        return eventDuration instanceof AllDayEventDuration;
    }

}
