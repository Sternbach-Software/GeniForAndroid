package app.familygem;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.theartofdev.edmodo.cropper.CropImage;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import app.familygem.constant.Choice;
import app.familygem.constant.Gender;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.EventActivity;
import app.familygem.detail.NameActivity;
import app.familygem.detail.NoteActivity;
import static app.familygem.Global.gc;

import app.familygem.list.NotesFragment;
import jp.wasabeef.picasso.transformations.BlurTransformation;

public class ProfileActivity extends AppCompatActivity {

	Person thisPerson;
	TabLayout tabLayout;
	Fragment[] tabs = new Fragment[3];
	String[] mainEventTags = {"BIRT", "BAPM", "RESI", "OCCU", "DEAT", "BURI"};
	List<Pair<String, String>> otherEvents; // List of tag + label

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		U.ensureGlobalGedcomNotNull(gc);
		thisPerson = (Person) Memory.getObject();
		// If the app goes into the background and is stopped, 'Memory' is reset and therefore 'thisPerson' will be null
		if( thisPerson == null && bundle != null ) {
			thisPerson = gc.getPerson(bundle.getString("idUno")); // The individual's id is saved in the bundle
			Memory.setFirst(thisPerson); // Otherwise the memory is without a stack
		}
		if( thisPerson == null ) return; // Rarely does the bundle not do its job
		Global.indi = thisPerson.getId();
		setContentView(R.layout.individuo);

		// Toolbar
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true); // brings up the back arrow and menu

		// Give the page view an adapter that manages the three tabs
		ViewPager viewPager = findViewById(R.id.profile_pager);
		SectionsPaginator sectionsPaginator = new SectionsPaginator();
		viewPager.setAdapter(sectionsPaginator);

		// "enriches"/populates the tablayout
		tabLayout = findViewById(R.id.profile_tabs);
		tabLayout.setupWithViewPager(viewPager); // otherwise the text in the TabItems disappears (?!)
		tabLayout.getTabAt(0).setText(R.string.media);
		tabLayout.getTabAt(1).setText(R.string.events);
		tabLayout.getTabAt(2).setText(R.string.relatives);
		tabLayout.getTabAt(getIntent().getIntExtra("scheda", 1)).select();

		// to animate the FAB
		final FloatingActionButton fab = findViewById(R.id.fab);
		viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled( int position,  // 0 between first and second, 1 between second and third...
										float offset, // 1->0 to the right, 0->1 to the left //delta? direction?
										int positionOffsetPixels ) {
				if( offset > 0 )
					fab.hide();
				else
					fab.show();
			}
			@Override
			public void onPageSelected( int position ) {}
			@Override
			public void onPageScrollStateChanged( int state ) {}
		});

		// List of other events
		String[] otherEventTags = {"CHR", "CREM", "ADOP", "BARM", "BATM", "BLES", "CONF", "FCOM", "ORDN", //Events
				"NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
				"CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL", // Attributes
				"_MILT"}; // User-defined
			/* Standard GEDCOM tags missing in the EventFact.DISPLAY_TYPE list:
				BASM (there is BATM instead) CHRA IDNO NMR FACT */
		otherEvents = new ArrayList<>();
		for( String tag : otherEventTags ) {
			EventFact event = new EventFact();
			event.setTag(tag);
			String label = event.getDisplayType();
			if( Global.settings.expert )
				label += " — " + tag;
			otherEvents.add(new Pair<>(tag, label));
		}
		// Alphabetically sorted by label
		Collections.sort(otherEvents, (item1, item2) -> item1.second.compareTo(item2.second));
	}

	class SectionsPaginator extends FragmentPagerAdapter {

		SectionsPaginator() {
			super( getSupportFragmentManager() );
		}

		@Override // it doesn't actually select but CREATE the three tabs
		public Fragment getItem(int position) {
			if( position == 0 )
				tabs[0] = new ProfileMediaFragment();
			else if( position == 1 )
				tabs[1] = new ProfileFactsFragment();
			else if( position == 2 )
				tabs[2] = new ProfileRelativesFragment();
			return tabs[position];
		}

		@Override
		public int getCount() {
			return 3;
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if( thisPerson == null || Global.edited )
			thisPerson = gc.getPerson(Global.indi);

		if( thisPerson == null ) { // going back to the Record of an individual who has been deleted
			onBackPressed();
			return;
		}


		// Person ID in the header
		TextView idView = findViewById(R.id.profile_id);
		if( Global.settings.expert ) {
			idView.setText("INDI " + thisPerson.getId());
			idView.setOnClickListener(v -> {
				U.editId(this, thisPerson, this::refresh);
			});
		} else idView.setVisibility(View.GONE);
		// Person name in the header
		CollapsingToolbarLayout toolbarLayout = findViewById(R.id.profile_toolbar_layout);
		toolbarLayout.setTitle(U.properName(thisPerson));
		toolbarLayout.setExpandedTitleTextAppearance(R.style.AppTheme_ExpandedAppBar);
		toolbarLayout.setCollapsedTitleTextAppearance(R.style.AppTheme_CollapsedAppBar);
		setImages();
		if( Global.edited ) {
			// Reload the 3 tabs coming back to the profile
			for( Fragment tab : tabs ) {
				if( tab != null ) { // At the first activity creation they are null
					getSupportFragmentManager().beginTransaction().detach(tab).commit();
					getSupportFragmentManager().beginTransaction().attach(tab).commit();
				}
			}
			invalidateOptionsMenu();
		}

		// Menu FAB
		findViewById(R.id.fab).setOnClickListener(vista -> {
			PopupMenu popup = new PopupMenu(this, vista);
			Menu menu = popup.getMenu();
			switch( tabLayout.getSelectedTabPosition() ) {
				case 0: // Individual Media
					menu.add(0, 10, 0, R.string.new_media);
					menu.add(0, 11, 0, R.string.new_shared_media);
					if( !gc.getMedia().isEmpty() )
						menu.add(0, 12, 0, R.string.link_shared_media);
					break;
				case 1: // Individual Events
					menu.add(0, 20, 0, R.string.name);
					// Gender
					if( Gender.getGender(thisPerson) == Gender.NONE )
						menu.add(0, 21, 0, R.string.sex);
					// Main events
					SubMenu eventSubMenu = menu.addSubMenu(R.string.event);
					CharSequence[] mainEventLabels = {getText(R.string.birth), getText(R.string.baptism), getText(R.string.residence), getText(R.string.occupation), getText(R.string.death), getText(R.string.burial)};
					int i;
					for( i = 0; i < mainEventLabels.length; i++ ) {
						CharSequence label = mainEventLabels[i];
						if( Global.settings.expert )
							label += " — " + mainEventTags[i];
						eventSubMenu.add(0, 40 + i, 0, label);
					}
					// Other events
					SubMenu otherSubMenu = eventSubMenu.addSubMenu(R.string.other);
					i = 0;
					for( Pair item : otherEvents ) {
						otherSubMenu.add(0, 50 + i, 0, (String)item.second);
						i++;
					}
					SubMenu subNote = menu.addSubMenu(R.string.note);
					subNote.add(0, 22, 0, R.string.new_note);
					subNote.add(0, 23, 0, R.string.new_shared_note);
					if( !gc.getNotes().isEmpty() )
						subNote.add(0, 24, 0, R.string.link_shared_note);
					if( Global.settings.expert ) {
						SubMenu subSource = menu.addSubMenu(R.string.source);
						subSource.add(0, 25, 0, R.string.new_source_note);
						subSource.add(0, 26, 0, R.string.new_source);
						if( !gc.getSources().isEmpty() )
							subSource.add(0, 27, 0, R.string.link_source);
					}
					break;
				case 2: // Individual family members
					menu.add(0, 30, 0, R.string.new_relative);
					if( U.containsConnectableIndividuals(thisPerson) )
						menu.add(0, 31, 0, R.string.link_person);
			}
			popup.show();
			popup.setOnMenuItemClickListener(item -> {
				CharSequence[] members = {getText(R.string.parent), getText(R.string.sibling), getText(R.string.partner), getText(R.string.child)};
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				switch( item.getItemId() ) {
					// Events tab
					case 0:
						break;
					// Media
					case 10: // Search local media
						F.displayMediaAppList(this, null, 2173, thisPerson);
						break;
					case 11: // Search for media objects
						F.displayMediaAppList(this, null, 2174, thisPerson);
						break;
					case 12: // Link Media in Gallery
						Intent principalIntent = new Intent(this, Principal.class);
						principalIntent.putExtra(Choice.MEDIA, true);
						startActivityForResult(principalIntent, 43614);
						break;
					case 20: // Create name
						Name name = new Name();
						name.setValue("//");
						thisPerson.addName(name);
						Memory.add(name);
						startActivity(new Intent(this, NameActivity.class));
						U.save(true, thisPerson);
						break;
					case 21: // Create sex
						String[] sexNames = {getString(R.string.male), getString(R.string.female), getString(R.string.unknown)};
						new AlertDialog.Builder(tabLayout.getContext())
								.setSingleChoiceItems(sexNames, -1, (dialog, i) -> {
									EventFact gender = new EventFact();
									gender.setTag("SEX");
									String[] sexValues = {"M", "F", "U"};
									gender.setValue(sexValues[i]);
									thisPerson.addEventFact(gender);
									dialog.dismiss();
									ProfileFactsFragment.updateMaritalRoles(thisPerson);
									refresh();
									U.save(true, thisPerson);
								}).show();
						break;
					case 22: // Create note
						Note note = new Note();
						note.setValue("");
						thisPerson.addNote(note);
						Memory.add(note);
						startActivity(new Intent(this, NoteActivity.class));
						// todo? DetailActivity.edit(View viewValue);
						U.save(true, thisPerson);
						break;
					case 23: // Create shared note
						NotesFragment.newNote(this, thisPerson);
						break;
					case 24: // Link shared note
						Intent intent = new Intent(this, Principal.class);
						intent.putExtra(Choice.NOTE, true);
						startActivityForResult(intent, 4074);
						break;
					case 25: // New source-note
						SourceCitation citation = new SourceCitation();
						citation.setValue("");
						thisPerson.addSourceCitation(citation);
						Memory.add(citation);
						startActivity(new Intent(this, SourceCitationActivity.class));
						U.save(true, thisPerson);
						break;
					case 26: // New source
						SourcesFragment.createNewSource(this, thisPerson);
						break;
					case 27: // Connect Source
						startActivityForResult(new Intent(this, Principal.class).putExtra(Choice.SOURCE, true), 50473);
						break;
					// Family tab
					case 30:// Connect new person
						if( Global.settings.expert ) {
							DialogFragment dialog = new NewRelativeDialog(thisPerson, null, null, true, null);
							dialog.show(getSupportFragmentManager(), "scegli");
						} else {
							builder.setItems(members, (dialog, quale) -> {
								Intent intent1 = new Intent(getApplicationContext(), IndividualEditorActivity.class);
								intent1.putExtra("idIndividuo", thisPerson.getId());
								intent1.putExtra("relazione", quale + 1);
								if( U.checkMultipleMarriages(intent1, this, null) )
									return;
								startActivity(intent1);
							}).show();
						}
						break;
					case 31: // Link existing person
						if( Global.settings.expert ) {
							DialogFragment dialog = new NewRelativeDialog(thisPerson, null, null, false, null);
							dialog.show(getSupportFragmentManager(), "scegli");
						} else {
							builder.setItems(members, (dialog, which) -> {
								Intent intent2 = new Intent(getApplication(), Principal.class);
								intent2.putExtra("idIndividuo", thisPerson.getId());
								intent2.putExtra(Choice.PERSON, true);
								intent2.putExtra("relazione", which + 1);
								if( U.checkMultipleMarriages(intent2, this, null) )
									return;
								startActivityForResult(intent2, 1401);
							}).show();
						}
						break;
					default:
						String keyTag = null;
						if( item.getItemId() >= 50 ) {
							keyTag = otherEvents.get(item.getItemId() - 50).first;
						} else if( item.getItemId() >= 40 )
							keyTag = mainEventTags[item.getItemId() - 40];
						if( keyTag == null )
							return false;
						EventFact newEvent = new EventFact();
						newEvent.setTag(keyTag);
						switch( keyTag ) {
							case "OCCU":
								newEvent.setValue("");
								break;
							case "RESI":
								newEvent.setPlace("");
								break;
							case "BIRT":
							case "DEAT":
							case "CHR":
							case "BAPM":
							case "BURI":
								newEvent.setPlace("");
								newEvent.setDate("");
						}
						thisPerson.addEventFact(newEvent);
						Memory.add(newEvent);
						startActivity(new Intent(this, EventActivity.class));
						U.save(true, thisPerson);
				}
				return true;
			});
		});
	}

	/* Display an image in the profile header
	   The blurred background image is displayed in most cases (jpg, png, gif...)
	   ToDo but not in case of a video preview, or image downloaded from the web with ZuppaMedia */
	void setImages() {
		ImageView imageView = findViewById(R.id.profile_image);
		Media media = F.showMainImageForPerson(Global.gc, thisPerson, imageView);
		// Same image blurred on background
		if( media != null ) {
			String path = F.mediaPath(Global.settings.openTree, media);
			Uri uri = null;
			if( path == null )
				uri = F.mediaUri(Global.settings.openTree, media);
			if( path != null || uri != null ) {
				RequestCreator creator;
				ImageView backImageView = findViewById(R.id.profile_background);
				backImageView.setColorFilter(ContextCompat.getColor(
						this, R.color.primary_grayed), PorterDuff.Mode.MULTIPLY);
				if( path != null )
					creator = Picasso.get().load("file://" + path);
				else
					creator = Picasso.get().load(uri);
				creator.resize(200, 200).centerCrop()
						.transform(new BlurTransformation(Global.context, 5, 1))
						.into(backImageView);
			}
		}
	}

	// Refresh everyting without recreating the activity
	public void refresh() {
		// Name in the header
		CollapsingToolbarLayout toolbarLayout = findViewById(R.id.profile_toolbar_layout);
		toolbarLayout.setTitle(U.properName(thisPerson));
		// Header images
		setImages();
		// ID in the header
		if( Global.settings.expert ) {
			TextView idView = findViewById(R.id.profile_id);
			idView.setText("INDI " + thisPerson.getId());
		}
		// 3 tabs
		for( Fragment tab : tabs ) {
			if( tab != null ) {
				FragmentManager manager = getSupportFragmentManager();
				manager.beginTransaction().detach(tab).commit();
				manager.beginTransaction().attach(tab).commit();
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("idUno", thisPerson.getId());
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == RESULT_OK ) {
			if( requestCode == 2173 ) { // File provided by an app becomes local media possibly cropped with Android Image Cropper
				Media media = new Media();
				media.setFileTag("FILE");
				thisPerson.addMedia(media);
				if( F.proposeCropping(this, null, data, media) ) { // returns true if it is a clipable image
					U.save(true, thisPerson);
					return;
				}
			} else if( requestCode == 2174 ) { // Files from apps in new Shared Media, with proposal to crop it
				Media media = GalleryFragment.newMedia(thisPerson);
				if( F.proposeCropping(this, null, data, media) ) {
					U.save(true, media, thisPerson);
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				// Get the image cropped by Android Image Cropper
				F.endImageCropping(data);
				U.save(true); // the switch date for Shared Media is already saved in the previous step
				           // todo pass it Global.mediaCropped ?
				return;
			} else if( requestCode == 43614 ) { // Media from GalleryFragment
				MediaRef mediaRef = new MediaRef();
				mediaRef.setRef( data.getStringExtra("mediaId") );
				thisPerson.addMediaRef( mediaRef );
			} else if( requestCode == 4074  ) { // Note
				NoteRef noteRef = new NoteRef();
				noteRef.setRef( data.getStringExtra("noteId") );
				thisPerson.addNoteRef( noteRef );
			} else if( requestCode == 50473  ) { // Source
				SourceCitation citaz = new SourceCitation();
				citaz.setRef( data.getStringExtra("sourceId") );
				thisPerson.addSourceCitation( citaz );
			} else if( requestCode == 1401  ) { // Relative
				Object[] modified = IndividualEditorActivity.addParent(
						data.getStringExtra("idIndividuo"), // corresponds to thisPerson.getId()
						data.getStringExtra("idParente"),
						data.getStringExtra("idFamiglia"),
						data.getIntExtra("relazione", 0),
						data.getStringExtra("collocazione") );
				U.save( true, modified );
				return;
			}
			U.save(true, thisPerson);
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) // if click back arrow in Crop Image
			Global.edited = true;
	}

	@Override
	public void onBackPressed() {
		Memory.clearStackAndRemove();
		super.onBackPressed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 0, 0, R.string.diagram);
		String[] familyLabels = Diagram.getFamilyLabels(this, thisPerson, null);
		if( familyLabels[0] != null )
			menu.add(0, 1, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 2, 0, familyLabels[1]);
		if( Global.settings.getCurrentTree().root == null || !Global.settings.getCurrentTree().root.equals(thisPerson.getId()) )
			menu.add(0, 3, 0, R.string.make_root);
		menu.add(0, 4, 0, R.string.modify);
		menu.add(0, 5, 0, R.string.delete);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case 0:	// Diagram
				U.askWhichParentsToShow(this, thisPerson, 1);
				return true;
			case 1: // Family as child
				U.askWhichParentsToShow(this, thisPerson, 2);
				return true;
			case 2: // Family as partner
				U.askWhichSpouseToShow(this, thisPerson, null);
				return true;
			case 3: // Set as root
				Global.settings.getCurrentTree().root = thisPerson.getId();
				Global.settings.save();
				Toast.makeText(this, getString(R.string.this_is_root, U.properName(thisPerson)), Toast.LENGTH_LONG).show();
				return true;
			case 4: // Edit
				Intent intent1 = new Intent(this, IndividualEditorActivity.class);
				intent1.putExtra("idIndividuo", thisPerson.getId());
				startActivity(intent1);
				return true;
			case 5:	// Delete
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
						.setPositiveButton(R.string.delete, (dialog, i) -> {
							Family[] families = ListOfPeopleFragment.deletePerson(this, thisPerson.getId());
							if( !U.checkFamilyItem(this, this::onBackPressed, true, families) )
								onBackPressed();
						}).setNeutralButton(R.string.cancel, null).show();
				return true;
			default:
				onBackPressed();
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		super.onRequestPermissionsResult(codice, permessi, accordi);
		F.permissionsResult(this, null, codice, permessi, accordi, thisPerson);
	}
}
