package app.familygem.visitor;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import java.util.ArrayList;
import java.util.List;
import app.familygem.Global;

/**
 * Visitor producing an ordered map of INLINE notes
 * */
public class NoteList extends TotalVisitor {

	public List<Note> noteList = new ArrayList<>();

	@Override
	boolean visit(Object obj, boolean isProgenitor) {
		if( obj instanceof NoteContainer
				&& !(!Global.settings.expert && (obj instanceof Source || obj instanceof Repository)) ) {
			NoteContainer container = (NoteContainer) obj;
			noteList.addAll(container.getNotes());
		}
		return true;
	}
}
