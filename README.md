# cs5248

To upload a video, first specify the video name via:
  pi.....comp.nus.edu.sg:port_no/updatedb/?videoname=YOUR_VIDEO_NAME&segno=VIDEO_SEG_NUMBER

The backend server will update database and create a folder named by YOUR_VIDEO_NAME under ./video/ directory.

The next step is uploading your video files. You can use POST method in:
  pi.....comp.nus.edu.sg:port_no/upload/
  
You must specify the video name and the segment number before uploading. The files will be stored in the new folder mentioned above.

To find out all video files in server, you can use the following API:
  pi.....comp.nus.edu.sg:port_no/videolist/
