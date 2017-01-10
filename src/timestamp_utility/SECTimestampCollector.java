package timestamp_utility;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

class SECTimestampCollector {
    private String output_dir;
    private String[] cik_data;
    private String[] filing_data;
    private String[] date_data;
    private final String base_url = "https://sec.gov/Archives/";

    void init(String _output_dir, String[][] box_data) {
        output_dir = _output_dir;
        cik_data = box_data[0];
        filing_data = box_data[1];
        date_data = box_data[2];
    }

    File collect() {
        File result_file = get_result_file();

        try {
            PrintWriter CSV = new PrintWriter(result_file);
            CSV.println("CIK,Disclosure,Disclosure Accepted,Last Modified");
            CSV.close();
            int max_len = Math.max(Math.max(cik_data.length, filing_data.length), date_data.length);
            for (int i = 0; i < max_len; i++) {
                if (!filing_data[i].equals("Press Release")) {
                    String csv_line = cik_data[i] + "," + filing_data[i] + "," + date_data[i];
                    String url_str = dateToIDX_URL(date_data[i]);
                    URL master_url = new URL(url_str);
                    System.out.println("For " + date_data[i] + ", Reading " + url_str);
                    BufferedReader master_file = new BufferedReader(new InputStreamReader(master_url.openStream()));
                    Boolean start_running = false;

                    for (String line = master_file.readLine(); line != null; line = master_file.readLine()) {
                        String[] current_line = line.split("\\|");
                        if (start_running || current_line.length == 5) {
                            if (!start_running) {
                                master_file.readLine();
                                current_line = master_file.readLine().split("|");
                                start_running = true;
                            }

                            String curr_cik = current_line[0];
                            if (curr_cik.equals(cik_data[i])) {
                                String curr_filing_type = current_line[2];
                                if (curr_filing_type.equals(filing_data[i])) {
                                    String curr_date = dateParse(current_line[3]);
                                    if (curr_date.equals(date_data[i].split(" ")[0])) {
                                        System.out.print("-> Found filing in masters: ");
                                        URL filing_url = new URL(base_url + current_line[4]);
                                        csv_line += "," + getTimestamp(filing_url);
                                    }
                                }
                            }
                        }
                    }
                    CSV = new PrintWriter(new FileOutputStream(result_file, true));
                    CSV.println(csv_line);
                    CSV.close();
                }

                float progress = ((float) i / (float) max_len) * 100;
                System.out.println(progress + "% complete");
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

        if (year_idx == 2) {
            month_idx = 0;
        } else {
            month_idx = 1;
        }

        String month_str = basic_date[month_idx];
        int month = Integer.parseInt(month_str);
        String qtr_num = Integer.toString(get_qtr_num(month));
        String year_str = Integer.toString(year);


        return base_url + "edgar/full-index/" + year_str + "/QTR" + qtr_num + "/master.idx";
    }

    private int get_qtr_num(int month) {
        int qtr_num = 0;
        if (month < 4) {
            qtr_num = 1;
        } else if (month >= 4 && month < 7) {
            qtr_num = 2;
        } else if (month >= 7 && month < 10) {
            qtr_num = 3;
        } else if (month >= 10 && month < 13) {
            qtr_num = 4;
        }

        return qtr_num;
    }

    private String dateParse(String raw_date) {
        String parsed = raw_date.replaceAll("-", "/");
        String[] new_date = parsed.split("/");
        if (new_date[1].charAt(0) == '0') {
            new_date[1] = new_date[1].substring(1);
        }
        if (new_date[2].charAt(0) == '0') {
            new_date[2] = new_date[2].substring(1);
        }
        String actual_date = new_date[1];
        actual_date += "/" + new_date[2];
        actual_date += "/" + new_date[0];
        parsed = actual_date;

        return parsed;
    }

    private String getTimestamp(URL u) {
        String upload_timestamp = "";

        try {
            HttpsURLConnection url_stream = (HttpsURLConnection) u.openConnection();

            Date date = new Date(url_stream.getLastModified());
            upload_timestamp = new SimpleDateFormat("MM-dd-yyyy HH:mm").format(date);

            String[] date_arr = upload_timestamp.split(" ")[0].split("-");
            if (date_arr[0].charAt(0) == '0') {
                date_arr[0] = date_arr[0].substring(1);
            }
            if (date_arr[1].charAt(0) == '0') {
                date_arr[1] = date_arr[1].substring(1);
            }

            String[] time = upload_timestamp.split(" ")[1].split(":");
            int mdt_mst_conversion = Integer.parseInt(time[0]) + 2;
            if (mdt_mst_conversion > 24) {
                mdt_mst_conversion -= 2;
                if (mdt_mst_conversion == 23) {
                    mdt_mst_conversion = 1;
                } else if (mdt_mst_conversion == 24) {
                    mdt_mst_conversion = 2;
                }
            }
            time[0] = Integer.toString(mdt_mst_conversion);

            String formatted_timestamp = date_arr[0] + "/" + date_arr[1];
            formatted_timestamp += "/" + date_arr[2] + " " + time[0] + ":" + time[1];
            upload_timestamp = formatted_timestamp;
            System.out.println("Formatted timestamp: " + upload_timestamp);
        } catch (IOException e) { /* ignore */ }

        return upload_timestamp;
    }
}