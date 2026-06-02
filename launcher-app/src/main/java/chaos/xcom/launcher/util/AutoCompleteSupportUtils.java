package chaos.xcom.launcher.util;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.matchers.TextMatcherEditor;
import ca.odell.glazedlists.swing.AutoCompleteSupport;

import javax.swing.*;
import java.util.Collection;

public class AutoCompleteSupportUtils {

    public static <E> AutoCompleteSupport<E> install(JComboBox<E> comboBox, Collection<E> items) {
        EventList<E> eventList = GlazedLists.eventList(items);
        return install(comboBox, eventList);
    }

    public static <E> AutoCompleteSupport<E> install(JComboBox<E> comboBox, EventList<E> items) {
        AutoCompleteSupport<E> support = AutoCompleteSupport.install(comboBox, items);
        support.setFilterMode(TextMatcherEditor.CONTAINS);
        return support;
    }
}
