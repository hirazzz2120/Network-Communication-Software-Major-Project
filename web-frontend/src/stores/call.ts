import { create } from 'zustand';
import type { Call, CallState } from '@/types/api';

interface CallStore {
  currentCall: Call | null;
  incomingCall: Call | null;
  peerConnection: RTCPeerConnection | null;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;

  setCurrentCall: (call: Call | null) => void;
  setIncomingCall: (call: Call | null) => void;
  updateCallState: (callId: string, state: CallState) => void;
  setPeerConnection: (pc: RTCPeerConnection | null) => void;
  setLocalStream: (stream: MediaStream | null) => void;
  setRemoteStream: (stream: MediaStream | null) => void;
  clearCall: () => void;
}

export const useCallStore = create<CallStore>((set) => ({
  currentCall: null,
  incomingCall: null,
  peerConnection: null,
  localStream: null,
  remoteStream: null,

  setCurrentCall: (call) => set({ currentCall: call }),

  setIncomingCall: (call) => set({ incomingCall: call }),

  updateCallState: (callId, state) =>
    set((store) => ({
      currentCall:
        store.currentCall?.callId === callId
          ? { ...store.currentCall, state }
          : store.currentCall,
      incomingCall:
        store.incomingCall?.callId === callId
          ? { ...store.incomingCall, state }
          : store.incomingCall,
    })),

  setPeerConnection: (pc) => set({ peerConnection: pc }),

  setLocalStream: (stream) => set({ localStream: stream }),

  setRemoteStream: (stream) => set({ remoteStream: stream }),

  clearCall: () =>
    set({
      currentCall: null,
      incomingCall: null,
      peerConnection: null,
      localStream: null,
      remoteStream: null,
    }),
}));
