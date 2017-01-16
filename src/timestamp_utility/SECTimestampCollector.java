package timestamp_utility;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

class SECTimestampCollector {
    private String output_dir;
    private String[] cik_data;
    private String[] filing_data;
    private String[] date_data;
    private final String base_url = "https://sec.gov/Archives/";
    private Controller ui;

    void init(String _output_dir, String[][] box_data, Controller _ui) {
        output_dir = _output_dir;
        cik_data = box_data[0];
        filing_data = box_data[1];
        date_data = box_data[2];
        ui = _ui;
    }

    File collect() {
        File result_file = get_result_file();

        try {
            PrintWriter CSV = new PrintWriter(result_file);
            CSV.println("CIK,Disclosure,Disclosure Accepted,Last Modified,HTML Accepted"); CSV.close();

            int max_len = cik_data.length;
            for (int i = 0; i < max_len; i++) {
                Boolean found = false;
                if (!filing_data[i].equals("Press Release")) {
                    String csv_line = cik_data[i] + "," + filing_data[i] + "," + date_data[i];
                    String url_str = dateToIDX_URL(date_data[i]);
                    URL master_url = new URL(url_str);
                    BufferedReader master = new BufferedReader(new InputStreamReader(master_url.openStream()));
                    Boolean start_running = false;

                    for (String line = master.readLine(); line != null; line = master.readLine()) {
                        String[] line_arr = line.split("\\|");
                        if (start_running || line_arr.length == 5) {
                            if (!start_running) {
                                master.readLine();
                                line_arr = master.readLine().split("\\|");
                                start_running = true;
                            }

                            if (line_arr[0].equals(cik_data[i]) && line_arr[2].equals(filing_data[i])) {
                                if (dateParse(line_arr[3], true).equals(date_data[i].split(" ")[0])) {
                                    URL filing_url = new URL(base_url + line_arr[4]);
                                    csv_line += "," + getTimestamp(filing_url);

                                    String accession_num = line_arr[4].replaceAll(".txt", "");
                                    String submission_url_str = base_url + "edgar/data/" + line_arr[0] + "/";
                                    submission_url_str +=  accession_num.replaceAll("-", "") + "/";
                                    submission_url_str += accession_num + "-index.htm";
                                    URL submission_url = new URL(submission_url_str);
                                    csv_line += "," + getAcceptedDate(submission_url);

                                    found = true;
                                }
                            }
                        }
                        if (found) break;
                    }
                    CSV = new PrintWriter(new FileOutputStream(result_file, true));
                    CSV.println(csv_line); CSV.close();
                }

                send_progress_to_ui(((float) i / (float) max_len) * 100);
            }

        } catch (IOException e) { /* ignore */ }
        return result_file;
    }

    private File get_result_file() {
        File result_file = new File(output_dir + "\\timestamp_result.csv");

        try {
            if (!result_file.createNewFile()) {
                for (int i = 0; !result_file.createNewFile(); i++) {
                    result_file = new File(output_dir + "\\timestamp_result (" + Integer.toString(i + 1) + ").csv");
                }
            }
        } catch (IOException e) { /* ignore */ }

        return result_file;
    }

    private String dateToIDX_URL(String date) {
        String[] basic_date = date.split(" ")[0].split("/");

        int year = 0;
        short year_idx = 0;
        short month_idx = 0;
        for (short i = 0; i < basic_date.length; i++) {
            int year_test = Integer.parseInt(basic_date[i]);
            if (year_test > 1000) {
                year = year_test;
                year_idx = i;
                break;
            }
        }

        if (year_idx == 2) month_idx = 0;
        else month_idx = 1;

        String month_str = basic_date[month_idx];
        int month = Integer.parseInt(month_str);
        String qtr_num = Integer.toString(get_qtr_num(month));
        String year_str = Integer.toString(year);


        return base_url + "edgar/full-index/" + year_str + "/QTR" + qtr_num + "/master.idx";
    }

    private int get_qtr_num(int month) {
        int qtr_num = 0;

        if (month < 4) qtr_num = 1;
        else if (month >= 4 && month < 7) qtr_num = 2;
        else if (month >= 7 && month < 10) qtr_num = 3;
        else if (month >= 10 && month < 13) qtr_num = 4;

        return qtr_num;
    }

    private String dateParse(String raw_date, Boolean reorder) {
        String parsed = raw_date.replaceAll("-", "/");
        String[] new_date = parsed.split("/");

        short month_idx, day_idx, year_idx;
        if (reorder) {
            month_idx = 1;
            day_idx = 2;
            year_idx = 0;
        } else {
            month_idx = 0;
            day_idx = 1;
            year_idx = 2;
        }

        if (new_date[month_idx].charAt(0) == '0') new_date[month_idx] = new_date[month_idx].substring(1);
        if (new_date[day_idx].charAt(0) == '0') new_date[day_idx] = new_date[day_idx].substring(1);

        return new_date[month_idx] + "/" + new_date[day_idx] + "/" + new_date[year_idx];
    }

    private String getTimestamp(URL u) {
        String upload_timestamp = "";

        try {
            HttpsURLConnection url_stream = (HttpsURLConnection) u.openConnection();

            Calendar c = Calendar.getInstance();
            c.setTime(new Date(url_stream.getLastModified()));
            c.add(Calendar.HOUR_OF_DAY, 2);
            upload_timestamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss").format(c.getTime());

            String time = upload_timestamp.split(" ")[1];
            upload_timestamp = dateParse(upload_timestamp.split(" ")[0].replaceAll("-", "/"), false);
            upload_timestamp += " " + time;
        } catch (IOException e) { /* ignore */ }

        return upload_timestamp;
    }

    private String getAcceptedDate(URL u) {
        String date_line = "";
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(u.openStream()));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.contains("Filing Date")) {
                    r.readLine(); r.readLine();
                    date_line = r.readLine();
                    break;
                }
            }
        } catch (IOException e) { /* ignore */ }

        String full_date = date_line.split(">")[1].split("<")[0];
        date_line = dateParse(full_date.split(" ")[0], true) + " " + full_date.split(" ")[1];

        return date_line;
    }

    private void send_progress_to_ui(float percent_complete) {
        new Thread(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    ui.progress_area.setText(String.format("%.2f%%", percent_complete));
                });

                return null;
            }
        }).start();
    }
}