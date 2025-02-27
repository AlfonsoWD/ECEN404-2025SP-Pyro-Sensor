#ifndef SPEAKER_H
#define SPEAKER_H

#ifdef __cplusplus
extern "C" {
#endif

void setup_pwm();
void start_alarm();
void stop_alarm();
void soundSpeaker(bool do_soundspeaker);

#ifdef __cplusplus
}
#endif

#endif // SPEAKER_H
