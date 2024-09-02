package com.tdcolvin.bleclient;

import android.content.Context;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.uwb.RangingParameters;
import androidx.core.uwb.RangingResult;
import androidx.core.uwb.UwbAddress;
import androidx.core.uwb.UwbComplexChannel;
import androidx.core.uwb.UwbControllerSessionScope;
import androidx.core.uwb.UwbDevice;
import androidx.core.uwb.UwbManager;
import androidx.core.uwb.rxjava3.UwbClientSessionScopeRx;
import androidx.core.uwb.rxjava3.UwbManagerRx;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.primitives.Shorts;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.disposables.Disposable;

public class UwbControllerCommunicator {

    // 초기화 변수
    private final Context context;
    private final UwbManager uwbManager;
    private final AtomicReference<Disposable> rangingResultObservable = new AtomicReference<>(null);
    private final AtomicReference<UwbControllerSessionScope> currentUwbSessionScope = new AtomicReference<>(null);

    public UwbControllerCommunicator(Context context) {
        this.context = context;
        this.uwbManager = UwbManager.createInstance(context);

        if (rangingResultObservable.get() != null) {
            rangingResultObservable.get().dispose();
            rangingResultObservable.set(null);
        }

        // UWB 세션 스코프를 컨트롤러로 설정
        new Thread(() -> currentUwbSessionScope.set(UwbManagerRx.controllerSessionScopeSingle(uwbManager).blockingGet())).start();
    }

    public String getUwbAddress() {
        UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
        if (controllerSessionScope != null) {
            UwbAddress localAddress = controllerSessionScope.getLocalAddress();
            return Shorts.fromByteArray(localAddress.getAddress()) + "";
        } else {
            return "UWB session not initialized";
        }
    }

    public String getUwbChannel() {
        UwbControllerSessionScope controllerSessionScope = (UwbControllerSessionScope) currentUwbSessionScope.get();
        if (controllerSessionScope != null) {
            return String.valueOf(controllerSessionScope.getUwbComplexChannel().getPreambleIndex());
        } else {
            return "Channel not initialized";
        }
    }

    public void startCommunication(String controlee) {
        try {
            int otherSideLocalAddress = Integer.parseInt(controlee);
            UwbAddress partnerAddress = new UwbAddress(Shorts.toByteArray((short) otherSideLocalAddress));

            UwbControllerSessionScope controllerSessionScope = currentUwbSessionScope.get();
            if (controllerSessionScope == null) {
                throw new IllegalStateException("UWB session not initialized");
            }

            // UWB 통신 파라미터 설정
            UwbComplexChannel uwbComplexChannel = controllerSessionScope.getUwbComplexChannel();
            RangingParameters partnerParameters = new RangingParameters(
                    RangingParameters.CONFIG_MULTICAST_DS_TWR,
                    12345,
                    0,
                    new byte[]{0, 0, 0, 0, 0, 0, 0, 0},
                    null,
                    uwbComplexChannel,
                    Collections.singletonList(new UwbDevice(partnerAddress)),
                    RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
            );

            rangingResultObservable.set(UwbClientSessionScopeRx.rangingResultsObservable(currentUwbSessionScope.get(), partnerParameters).subscribe(
                    rangingResult -> {
                        if (rangingResult instanceof RangingResult.RangingResultPosition) {
                            RangingResult.RangingResultPosition rangingResultPosition = (RangingResult.RangingResultPosition) rangingResult;
                            if (rangingResultPosition.getPosition().getDistance() != null) {
                                System.out.println("Distance: " + rangingResultPosition.getPosition().getDistance().getValue());
                            }
                        } else {
                            System.out.println("CONNECTION LOST");
                        }
                    },
                    System.out::println,
                    () -> System.out.println("Completed the observing of RangingResults")
            ));

        } catch (NumberFormatException e) {
            System.out.println("Caught Exception: " + e);
        }
    }
}
