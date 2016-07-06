#include <com_example_ss_jnilive555_MainActivity.h>

/**********
This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the
Free Software Foundation; either version 2.1 of the License, or (at your
option) any later version. (See <http://www.gnu.org/copyleft/lesser.html>.)

This library is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
more details.

You should have received a copy of the GNU Lesser General Public License
along with this library; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
**********/
// Copyright (c) 1996-2016, Live Networks, Inc.  All rights reserved
// A test program that demonstrates how to stream - via unicast RTP
// - various kinds of file on demand, using a built-in RTSP server.
// main program

#include "liveMedia.hh"
#include "BasicUsageEnvironment.hh"

UsageEnvironment* env;

// To make the second and subsequent client for each stream reuse the same
// input stream as the first client (rather than playing the file from the
// start for each client), change the following "False" to "True":
Boolean reuseFirstSource = True;

// To stream *only* MPEG-1 or 2 video "I" frames
// (e.g., to reduce network bandwidth),
// change the following "False" to "True":
Boolean iFramesOnly = False;

char inputFileName[2048];

static void announceStream(RTSPServer* rtspServer, ServerMediaSession* sms,
               char const* streamName, char const* inputFileName); // fwd

static char newDemuxWatchVariable;

static MatroskaFileServerDemux* matroskaDemux;
static void onMatroskaDemuxCreation(MatroskaFileServerDemux* newDemux, void* /*clientData*/) {
  matroskaDemux = newDemux;
  newDemuxWatchVariable = 1;
}

static OggFileServerDemux* oggDemux;
static void onOggDemuxCreation(OggFileServerDemux* newDemux, void* /*clientData*/) {
  oggDemux = newDemux;
  newDemuxWatchVariable = 1;
}

int Server_Main(const char *fileName) {
  // Begin by setting up our usage environment:
  TaskScheduler* scheduler = BasicTaskScheduler::createNew();
  env = BasicUsageEnvironment::createNew(*scheduler);

  UserAuthenticationDatabase* authDB = NULL;
#ifdef ACCESS_CONTROL
  // To implement client access control to the RTSP server, do the following:
  authDB = new UserAuthenticationDatabase;
  authDB->addUserRecord("username1", "password1"); // replace these with real strings
  // Repeat the above with each <username>, <password> that you wish to allow
  // access to the server.
#endif

  // Create the RTSP server:
  RTSPServer* rtspServer = RTSPServer::createNew(*env, 8554, authDB);
  if (rtspServer == NULL) {
    *env << "Failed to create RTSP server: " << env->getResultMsg() << "\n";
    exit(1);
  }

  char const* descriptionString
    = "Session streamed by \"testOnDemandRTSPServer\"";

  // Set up each of the possible streams that can be served by the
  // RTSP server.  Each such stream is implemented using a
  // "ServerMediaSession" object, plus one or more
  // "ServerMediaSubsession" objects for each audio/video substream.

  // A MP3 audio stream (actually, any MPEG-1 or 2 audio file will work):
  // To stream using 'ADUs' rather than raw MP3 frames, uncomment the following:
//#define STREAM_USING_ADUS 1
  // To also reorder ADUs before streaming, uncomment the following:
//#define INTERLEAVE_ADUS 1
  // (For more information about ADUs and interleaving,
  //  see <http://www.live555.com/rtp-mp3/>)


  // A Matroska ('.mkv') file, with video+audio+subtitle streams:
  {
    char const* streamName = "matroskaFileTest";
    memset(inputFileName, 0x00, 2048);
    strcpy(inputFileName, fileName);
    ServerMediaSession* sms
      = ServerMediaSession::createNew(*env, streamName, streamName,
                      descriptionString);

    newDemuxWatchVariable = 0;
    MatroskaFileServerDemux::createNew(*env, inputFileName, onMatroskaDemuxCreation, NULL);
    env->taskScheduler().doEventLoop(&newDemuxWatchVariable);

    Boolean sessionHasTracks = False;
    ServerMediaSubsession* smss;
    while ((smss = matroskaDemux->newServerMediaSubsession()) != NULL) {
      sms->addSubsession(smss);
      sessionHasTracks = True;
    }
    if (sessionHasTracks) {
      rtspServer->addServerMediaSession(sms);
    }
    // otherwise, because the stream has no tracks, we don't add a ServerMediaSession to the server.

    announceStream(rtspServer, sms, streamName, inputFileName);
  }

  // Also, attempt to create a HTTP server for RTSP-over-HTTP tunneling.
  // Try first with the default HTTP port (80), and then with the alternative HTTP
  // port numbers (8000 and 8080).

  if (rtspServer->setUpTunnelingOverHTTP(80) || rtspServer->setUpTunnelingOverHTTP(8000) || rtspServer->setUpTunnelingOverHTTP(8080)) {
    *env << "\n(We use port " << rtspServer->httpServerPortNum() << " for optional RTSP-over-HTTP tunneling.)\n";
  } else {
    *env << "\n(RTSP-over-HTTP tunneling is not available.)\n";
  }

  env->taskScheduler().doEventLoop(); // does not return

  return 0; // only to prevent compiler warning
}

static void announceStream(RTSPServer* rtspServer, ServerMediaSession* sms,
               char const* streamName, char const* inputFileName) {
  char* url = rtspServer->rtspURL(sms);
  UsageEnvironment& env = rtspServer->envir();
  env << "\n\"" << streamName << "\" stream, from the file \""
      << inputFileName << "\"\n";
  env << "Play this stream using the URL \"" << url << "\"\n";
  delete[] url;
}

JNIEXPORT jstring JNICALL Java_com_example_ss_jnilive555_MainActivity_startServerMain
        (JNIEnv *env, jobject thiz, jstring jstr){
    const char* str;
    jboolean isCopy = JNI_TRUE;
    str = env->GetStringUTFChars(jstr, &isCopy);
    int status = Server_Main(str);
    env->ReleaseStringUTFChars(jstr, str);
    char c[100];
    sprintf(c,"Hello from JNI %d", status);
    return env->NewStringUTF(c);
}
