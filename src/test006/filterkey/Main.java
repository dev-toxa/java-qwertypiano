package test006.filterkey;

import java.awt.BorderLayout;
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
import javax.swing.text.AbstractDocument;

public class Main extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTextArea textArea = null;
	private Map<String, Integer> keyMap = null;
	private MidiDevice outDevice = null;
	private Receiver receiver = null; // 리시버를 전역변수로 관리
	private String targetPortName = "loopMIDI Port";
	private Map<Integer, Boolean> keyStateMap = new HashMap<>();
	private boolean isSustainToggled = false;
	private int octaveOffset = 0; // 옥타브 변동값 (1당 12단계)
	private int transposeOffset = 0; // 반음 변동값 (왼쪽/오른쪽 방향키)
	private AbstractDocument doc = null;
	private boolean sw = false;
	private void sendSustainCommand(boolean isOn) {
		try {
			if (receiver != null) {
				ShortMessage msg = new ShortMessage();
				// 127은 On(밟음), 0은 Off(뗌)
				int value = isOn ? 127 : 0;
				// 0xB0(Control Change), 64(Sustain Pedal)
				msg.setMessage(ShortMessage.CONTROL_CHANGE, 0, 64, value);
				receiver.send(msg, -1);
				System.out.println("Sustain " + (isOn ? "ON" : "OFF"));
			}
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
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
						outDevice.open(); // 여기서 딱 한 번만 엽니다.
						receiver = outDevice.getReceiver(); // 리시버 미리 생성
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
		String[] keyArray = { "1", "!", "2", "@", "3", "4", "$", "5", "%", "6", "^", "7", "8", "*", "9", "0", "q", "Q",
				"w", "W", "e", "E", "r", "t", "T", "y", "Y", "u", "i", "I", "o", "O", "p", "P", "a", "s", "S", "d", "D",
				"f", "g", "G", "h", "H", "j", "J", "k", "l", "L", "z", "Z", "x", "c", "C", "v", "V", "b", "B", "n", "m",
				"M" };

		for (int i = 0; i < keyArray.length; i++) {
			keyMap.put(keyArray[i], i + 36);
		}
	}

	private void setTextArea() {
		textArea = new JTextArea();
		textArea.setLineWrap(true); // 자동 줄바꿈
	    textArea.setWrapStyleWord(true);
		doc = (AbstractDocument) textArea.getDocument();
		doc.setDocumentFilter(new RepeatFilter());
		textArea.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				int keyCode = e.getKeyCode();
				// 백스페이스 감지 (토글 로직)
				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && e.isShiftDown()) {
				    isSustainToggled = !isSustainToggled; // 상태 반전
				    sendSustainCommand(isSustainToggled); // MIDI 전송
				    return; // 이벤트 소비 (백스페이스 문자 입력 방지)
				}

				// 1. 방향키 제어 로직
				switch (keyCode) {
				case KeyEvent.VK_UP: // 옥타브 위로
					octaveOffset += 12;
					updateStatus();
					return;
				case KeyEvent.VK_DOWN: // 옥타브 아래로
					octaveOffset -= 12;
					updateStatus();
					return;
				case KeyEvent.VK_RIGHT: // 반음 위로 (Transpose)
					transposeOffset += 1;
					updateStatus();
					return;
				case KeyEvent.VK_LEFT: // 반음 아래로 (Transpose)
					transposeOffset -= 1;
					updateStatus();
					return;
				}

				// 2. 기존 연주 로직 (변수 적용)
				String charKey = String.valueOf(e.getKeyChar());
				Integer baseNote = keyMap.get(charKey);

				if (baseNote != null) {
					// 기본값(baseNote) + 옥타브 + 조옮김 값을 합산
					int finalNote = baseNote + octaveOffset + transposeOffset;

					// MIDI 범위(0~127) 체크
					if (finalNote >= 0 && finalNote <= 127 && !keyStateMap.getOrDefault(finalNote, false)) {
						keyStateMap.put(finalNote, true);
						noteON(finalNote);
					}
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				// 백스페이스 뗄 때는 아무 동작 안 함
				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
					return;
				String charKey = String.valueOf(e.getKeyChar());
				Integer baseNote = keyMap.get(charKey);

				if (baseNote != null) {
					int finalNote = baseNote + octaveOffset + transposeOffset;
					keyStateMap.put(finalNote, false);
					noteOFF(finalNote);
				}

			}
		});
		Thread th = new Thread() {
			@Override
			public void run() {
				try {
					while (true) {
						textArea.append(" ");
						textArea.setCaretPosition(textArea.getDocument().getLength());
						Thread.sleep(250);
					}
				} catch (InterruptedException e) {
					// TODO th-generated catch block
					e.printStackTrace();
				}
			}

		};
//		th.start();
	}

	private void updateStatus() {
		String status = String.format("Octave: %+d | Transpose: %+d | Sustain: %s", octaveOffset / 12, transposeOffset,
				isSustainToggled ? "ON" : "OFF");
		setTitle("MIDI Controller - " + status);
		System.out.println(status);
	}

	private void setFrame() {
		setLayout(new BorderLayout());
		add(textArea, BorderLayout.CENTER);
		textArea.setFont(new Font("Consolas", Font.PLAIN, 24));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(600, 400);
		setVisible(true);
	}

	public Main() {
		initMidiPort();
		setKeyMap();
		setTextArea();
		setFrame();
	}

	public static void main(String[] args) {
		new Main();
	}
}