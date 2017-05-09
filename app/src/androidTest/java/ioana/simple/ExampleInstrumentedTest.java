package ioana.simple;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.mock.MockContext;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.protobuf.ByteString;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest  {
    @Rule
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(
            MainActivity.class);


    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        int time = 1000;
        String Apid = "AP1";
        ProofProtos.SignedLocnProof locnProof = ProofProtos.SignedLocnProof.newBuilder()
                .setLocnproof(ProofProtos.LocnProof.newBuilder()
                        .setApid(ByteString.copyFromUtf8(Apid))
                        .setApnonce(ByteString.EMPTY)
                        .setEkey(ByteString.EMPTY)
                        .setTime(time)
                        .setUid(ByteString.EMPTY)
                        .setUnonce(ByteString.EMPTY)
                        .setVaultKey(ByteString.EMPTY))
                .setSig(ByteString.EMPTY).build();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivityRule.getActivity(),
                R.layout.activity_main_proof_list_item);
        ListView lv = Mockito.mock(ListView.class);
        when(lv.getAdapter()).thenReturn(adapter);
        Singleton.getInstance().setProofNameList(lv);
        Singleton.getInstance().addToList(locnProof);
        assertEquals(1, adapter.getCount());
        assertEquals(Apid + " at " + (new Date(time)).toString(), adapter.getItem(0));
    }
}
