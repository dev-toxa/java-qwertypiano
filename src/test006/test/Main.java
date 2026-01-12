package test006.test;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.swing.JFrame;
import javax.swing.JTextArea;

public class Main extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTextArea textArea = null;
	private Map<String, Integer> keyMap = null;
	private MidiDevice outDevice = null;
	private Receiver receiver = null;
	private String targetPortName = "loopMIDI Port";
	private Map<Integer, Boolean> keyStateMap = new HashMap<>();
	
	private boolean isSustainToggled = false;
	private int octaveOffset = 0;
	private int transposeOffset = 0;

	// 자동 페달 관련 변수
	private Thread autoPedalThread = null;
	private int pedalInterval = 100; // 4분음표 간격 (ms단위, 600ms = 약 100BPM)

	private void sendSustainCommand(boolean isOn) {
		try {
			if (receiver != null) {
				ShortMessage msg = new ShortMessage();
				int value = isOn ? 127 : 0;
				msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, 64, value);
				receiver.send(msg, -1);
			}
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
	}

	// 서스테인이 켜져있을 때 주기적으로 페달을 갈아주는 스레드
	private void manageAutoPedal(boolean start) {
		if (start) {
			if (autoPedalThread != null && autoPedalThread.isAlive()) return;
			
			autoPedalThread = new Thread(() -> {
				try {
					while (isSustainToggled) {
						// 1. 페달 뗌 (잔향 제거)
						sendSustainCommand(false);
						Thread.sleep(50); // 아주 잠깐 뗌
						
						// 2. 다시 밟음
						sendSustainCommand(true);
						
						// 3. 지정된 시간(4분음표 등)만큼 대기
						Thread.sleep(pedalInterval);
					}
				} catch (InterruptedException e) {
					// 스레드 종료
				}
			});
			autoPedalThread.setDaemon(true);
			autoPedalThread.start();
		} else {
			if (autoPedalThread != null) {
				autoPedalThread.interrupt();
			}
			sendSustainCommand(false); // 최종적으로 끔
		}
	}

	private void initMidiPort() {
		try {
			MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
			for (MidiDevice.Info info : infos) {
				if (info.getName().contains(targetPortName)) {
					MidiDevice device = MidiSystem.getMidiDevice(info);
					if (device.getMaxReceivers() != 0) {
						outDevice = device;
						outDevice.open();
						receiver = outDevice.getReceiver();
						System.out.println("연결 성공: " + info.getName());
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void noteON(int noteNumber) {
		try {
			if (receiver != null) {
				ShortMessage myMsg = new ShortMessage();
				myMsg.setMessage(ShortMessage.NOTE_ON, 0, noteNumber, 93);
				receiver.send(myMsg, -1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void noteOFF(int noteNumber) {
		try {
			if (receiver != null) {
				ShortMessage myMsg = new ShortMessage();
				myMsg.setMessage(ShortMessage.NOTE_OFF, 0, noteNumber, 93);
				receiver.send(myMsg, -1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setKeyMap() {
		keyMap = new HashMap<>();
		String[] keyArray = { "1", "!", "2", "@", "3", "4", "$", "5", "%", "6", "^", "7", "8", "*", "9","(", "0", "q", "Q",
				"w", "W", "e", "E", "r", "t", "T", "y", "Y", "u", "i", "I", "o", "O", "p", "P", "a", "s", "S", "d", "D",
				"f", "g", "G", "h", "H", "j", "J", "k", "l", "L", "z", "Z", "x", "c", "C", "v", "V", "b", "B", "n", "m",
				"M" };

		for (int i = 0; i < keyArray.length; i++) {
			keyMap.put(keyArray[i], i + 36);
		}
	}

	private void setTextArea() {
		textArea = new JTextArea();
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int keyCode = e.getKeyCode();

				// Shift + Backspace: 서스테인 토글 + 자동 페달 시작/중지
//				if (keyCode == KeyEvent.VK_BACK_SPACE && e.isShiftDown()) {
//					isSustainToggled = !isSustainToggled;
//					manageAutoPedal(isSustainToggled);
//					updateStatus();
//					return;
//				}

				// 방향키 제어
				switch (keyCode) {
				case KeyEvent.VK_UP: octaveOffset += 12; updateStatus(); return;
				case KeyEvent.VK_DOWN: octaveOffset -= 12; updateStatus(); return;
				case KeyEvent.VK_RIGHT: transposeOffset += 1; updateStatus(); return;
				case KeyEvent.VK_LEFT: transposeOffset -= 1; updateStatus(); return;
				}

				String charKey = String.valueOf(e.getKeyChar());
				Integer baseNote = keyMap.get(charKey);

				if (baseNote != null) {
					int finalNote = baseNote + octaveOffset + transposeOffset;
					if (finalNote >= 0 && finalNote <= 127 && !keyStateMap.getOrDefault(finalNote, false)) {
						keyStateMap.put(finalNote, true);
						noteON(finalNote);
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) return;
				String charKey = String.valueOf(e.getKeyChar());
				Integer baseNote = keyMap.get(charKey);

				if (baseNote != null) {
					int finalNote = baseNote + octaveOffset + transposeOffset;
					keyStateMap.put(finalNote, false);
					noteOFF(finalNote);
				}
			}
		});
	}

	private void updateStatus() {
		String status = String.format("Octave: %+d | Transpose: %+d | Sustain: %s (%dms)", 
				octaveOffset / 12, transposeOffset, isSustainToggled ? "AUTO" : "OFF", pedalInterval);
		setTitle("MIDI Controller - " + status);
	}

	private void setFrame() {
		setLayout(new BorderLayout());
		add(textArea, BorderLayout.CENTER);
		textArea.setFont(new Font("Consolas", Font.PLAIN, 24));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 500);
		textArea.setBackground(Color.black);
		textArea.setForeground(Color.white);
		setLocationRelativeTo(null);
		setVisible(true);
	}

	public Main() {
		initMidiPort();
		setKeyMap();
		setTextArea();
		setFrame();
		updateStatus();
	}

	public static void main(String[] args) {
		new Main();
	}
}