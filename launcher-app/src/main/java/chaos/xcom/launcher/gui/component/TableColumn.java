package chaos.xcom.launcher.gui.component;

import chaos.xcom.launcher.mod.dto.Mod;

import java.util.function.Function;

public class TableColumn<T, R> {
    public final String name;
    public final Class<R> type;
    public final Function<T, R> extractor;
    public final Function<R, String> renderAs;

    public TableColumn(String name, Class<R> type, Function<T, R> extractor) {
        this(name, type, extractor, null);
    }

    public TableColumn(String name, Class<R> type, Function<T, R> extractor, Function<R, String> renderAs) {
        this.name = name;
        this.type = type;
        this.extractor = extractor;
        this.renderAs = renderAs;
    }
}
