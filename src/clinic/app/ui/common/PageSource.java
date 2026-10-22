package clinic.app.ui.common;
import clinic.service.PageResult;
@FunctionalInterface public interface PageSource<T> {
    PageResult<T> fetch(int page, int size, String query) throws Exception;
}
