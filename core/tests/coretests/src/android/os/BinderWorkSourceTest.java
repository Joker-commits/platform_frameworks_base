/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test whether Binder calls work source is propagated correctly.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BinderWorkSourceTest {
    private static Context sContext;
    private static final int UID = 100;
    private static final int SECOND_UID = 200;
    private static final int UID_NONE = ThreadLocalWorkSource.UID_NONE;

    private IBinderWorkSourceService mService;
    private IBinderWorkSourceNestedService mNestedService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IBinderWorkSourceService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private ServiceConnection mNestedConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mNestedService = IBinderWorkSourceNestedService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName name) {
            mNestedService = null;
        }
    };

    @BeforeClass
    public static void setUpOnce() throws Exception {
        sContext = InstrumentationRegistry.getContext();
    }

    @Before
    public void setUp() throws Exception {
        sContext.bindService(
                new Intent(sContext, BinderWorkSourceService.class),
                mConnection, Context.BIND_AUTO_CREATE);
        sContext.bindService(
                new Intent(sContext, BinderWorkSourceNestedService.class),
                mNestedConnection, Context.BIND_AUTO_CREATE);

        final long timeoutMs = System.currentTimeMillis() + 30_000;
        while ((mService == null || mNestedService == null)
                && System.currentTimeMillis() < timeoutMs) {
            Thread.sleep(1_000);
        }
        assertNotNull("Gave up waiting for BinderWorkSourceService", mService);
        assertNotNull("Gave up waiting for BinderWorkSourceNestedService", mNestedService);
    }

    @After
    public void tearDown() throws Exception {
        sContext.unbindService(mConnection);
        sContext.unbindService(mNestedConnection);
    }

    @Test
    public void setWorkSource() throws Exception {
        Binder.setCallingWorkSourceUid(UID);
        assertEquals(UID, mService.getIncomingWorkSourceUid());
        assertEquals(UID, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void clearWorkSource() throws Exception {
        Binder.setCallingWorkSourceUid(UID);
        Binder.clearCallingWorkSource();
        assertEquals(UID_NONE, mService.getIncomingWorkSourceUid());
        assertEquals(UID_NONE, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void setWorkSource_propagatedForMultipleCalls() throws Exception {
        Binder.setCallingWorkSourceUid(UID);
        assertEquals(UID, mService.getIncomingWorkSourceUid());
        assertEquals(UID, mService.getIncomingWorkSourceUid());
        assertEquals(UID, mService.getIncomingWorkSourceUid());
        assertEquals(UID, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void restoreWorkSource() throws Exception {
        Binder.setCallingWorkSourceUid(UID);
        long token = Binder.clearCallingWorkSource();
        Binder.restoreCallingWorkSource(token);

        assertEquals(UID, mService.getIncomingWorkSourceUid());
        assertEquals(UID, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void nestedSetWorkSoucePropagated() throws Exception {
        Binder.setCallingWorkSourceUid(UID);

        int[] workSources = mNestedService.nestedCallWithWorkSourceToSet(SECOND_UID);
        assertEquals(UID, workSources[0]);
        // UID set in ested call.
        assertEquals(SECOND_UID, workSources[1]);
        // Initial work source restored.
        assertEquals(UID, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void nestedSetWorkSouceDoesNotEnablePropagation() throws Exception {
        int[] workSources = mNestedService.nestedCallWithWorkSourceToSet(UID);
        assertEquals(UID_NONE, workSources[0]);
        // UID set in ested call.
        assertEquals(UID, workSources[1]);
        // Initial work source restored.
        assertEquals(UID_NONE, Binder.getCallingWorkSourceUid());
    }

    @Test
    public void nestedSetWorkSouceNotPropagated() throws Exception {
        Binder.setCallingWorkSourceUid(UID);

        int[] workSources = mNestedService.nestedCall();
        assertEquals(UID, workSources[0]);
        // No UID propagated.
        assertEquals(UID_NONE, workSources[1]);
        // Initial work source restored.
        assertEquals(UID, Binder.getCallingWorkSourceUid());
    }
}
