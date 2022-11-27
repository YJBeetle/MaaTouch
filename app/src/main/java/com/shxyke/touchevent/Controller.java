package com.shxyke.touchevent;

import com.shxyke.touchevent.wrappers.InputManager;
import com.shxyke.touchevent.wrappers.ServiceManager;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

public class Controller {

    private static final int DEFAULT_DEVICE_ID = 0;
    private static final int DEFAULT_SOURCE = InputDevice.SOURCE_TOUCHSCREEN;

    private final ServiceManager serviceManager;

    private long lastTouchDown;
    private final PointersState pointersState = new PointersState();
    private final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[PointersState.MAX_POINTERS];
    private final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[PointersState.MAX_POINTERS];

    public Controller(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
        initPointers();
    }

    private void initPointers() {
        for (int i = 0; i < PointersState.MAX_POINTERS; ++i) {
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;

            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.orientation = 0;
            coords.size = 0;

            pointerProperties[i] = props;
            pointerCoords[i] = coords;
        }
    }

    public void resetAll() {
        int count = pointersState.size();
        for (int i = 0; i < count; ++i) {
            Pointer pointer = pointersState.get(0);
            this.injectTouch(MotionEvent.ACTION_UP, pointer.getId(), pointer.getPoint(), 0);
        }
    }

    public boolean injectTouchDown(long pointerId, Point point, float pressure) {
        return this.injectTouch(MotionEvent.ACTION_DOWN, pointerId, point, pressure);
    }

    public boolean injectTouchMove(long pointerId, Point point, float pressure) {
        return this.injectTouch(MotionEvent.ACTION_MOVE, pointerId, point, pressure);
    }

    public boolean injectTouchUp(long pointerId) {
        int pointerIndex = pointersState.getPointerIndex(pointerId);
        if (pointerIndex == -1) {
            Ln.w("No such pointer");
            return false;
        }
        Pointer pointer = pointersState.get(pointerIndex);
        return this.injectTouch(MotionEvent.ACTION_UP, pointerId, pointer.getPoint(), 0);
    }

    public boolean injectTouch(int action, long pointerId, Point point, float pressure) {
        long now = SystemClock.uptimeMillis();

        if (point == null) {
            Ln.w("Ignore touch event, it was generated for a different device size");
            return false;
        }

        int pointerIndex = pointersState.getPointerIndex(pointerId);
        if (pointerIndex == -1) {
            Ln.w("Too many pointers for touch event");
            return false;
        }
        Pointer pointer = pointersState.get(pointerIndex);
        pointer.setPoint(point);
        pointer.setPressure(pressure);
        pointer.setUp(action == MotionEvent.ACTION_UP);

        int pointerCount = pointersState.update(pointerProperties, pointerCoords);

        if (pointerCount == 1) {
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchDown = now;
            }
        } else {
            // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
            if (action == MotionEvent.ACTION_UP) {
                action = MotionEvent.ACTION_POINTER_UP | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            } else if (action == MotionEvent.ACTION_DOWN) {
                action = MotionEvent.ACTION_POINTER_DOWN | (pointerIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            }
        }

        MotionEvent event = MotionEvent
                .obtain(lastTouchDown, now, action, pointerCount, pointerProperties, pointerCoords, 0, 0, 1f, 1f, DEFAULT_DEVICE_ID, 0,
                        DEFAULT_SOURCE, 0);
        return serviceManager.getInputManager().injectInputEvent(event, InputManager.INJECT_MODE_ASYNC);
    }
}