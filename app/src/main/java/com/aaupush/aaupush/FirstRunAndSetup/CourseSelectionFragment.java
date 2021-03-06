package com.aaupush.aaupush.FirstRunAndSetup;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.aaupush.aaupush.DBHelper;
import com.aaupush.aaupush.MainActivity;
import com.aaupush.aaupush.PushUtils;
import com.aaupush.aaupush.R;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * A {@link Fragment} that is used to select courses to follow.
 * Use the {@link CourseSelectionFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CourseSelectionFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "CourseSectionSelection";

    // Fragment Modes
    public static final int MODE_COURSES = 1;
    public static final int MODE_SECTIONS = 2;

    //
    private int fragmentMode;

    // Args for Course Mode
    String sectionCode;
    boolean isSectionPrimary;

    // Args for Section Mode
    int studyFieldID;

    // Request Queue
    RequestQueue requestQueue;

    // Main RecyclerView
    RecyclerView mainRV;

    // Root View
    View view;

    // SharedPreferences
    SharedPreferences preferences;

    public CourseSelectionFragment() {
        // Required empty public constructor
    }


    public static CourseSelectionFragment newInstance(String sectionCode, boolean isSectionPrimary) {
        CourseSelectionFragment courseSelectionFragment = new CourseSelectionFragment();
        courseSelectionFragment.sectionCode = sectionCode;
        courseSelectionFragment.isSectionPrimary = isSectionPrimary;
        courseSelectionFragment.fragmentMode = MODE_COURSES;
        return courseSelectionFragment;
    }

    public static CourseSelectionFragment newInstance(int studyFieldID) {
        CourseSelectionFragment courseSelectionFragment = new CourseSelectionFragment();
        courseSelectionFragment.studyFieldID = studyFieldID;
        courseSelectionFragment.fragmentMode = MODE_SECTIONS;
        return courseSelectionFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_course_selection, container, false);

        preferences = getContext().getSharedPreferences(PushUtils.SP_KEY_NAME, Context.MODE_PRIVATE);

        requestQueue = Volley.newRequestQueue(getContext().getApplicationContext());

        // Link Views
        mainRV = (RecyclerView)view.findViewById(R.id.course_section_rv);
        Button finishButton = (Button)view.findViewById(R.id.finish_setup_btn);
        Button followMoreButton = (Button)view.findViewById(R.id.follow_more_btn);

        // Set on click listeners for the buttons
        finishButton.setOnClickListener(this);
        followMoreButton.setOnClickListener(this);

        // Hide follow more button based on the fragment mode
        if (fragmentMode == MODE_SECTIONS) {
            followMoreButton.setVisibility(View.INVISIBLE);
        }

        // Set up mainRV
        mainRV.setLayoutManager(new LinearLayoutManager(getContext()));
        mainRV.setHasFixedSize(false);
        mainRV.addItemDecoration(new DividerItemDecoration(getContext(),
                new LinearLayoutManager(getContext()).getOrientation()));
        mainRV.setItemAnimator(new DefaultItemAnimator());

        setAdapter();

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.follow_more_btn:
                // Add the already selected courses to the db
                addCoursesInAdapterToDb();

                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager
                        .beginTransaction()
                        .replace(R.id.first_run_activity,
                                CourseSelectionFragment.newInstance(preferences.getInt(PushUtils.SP_STUDY_FIELD_ID, 0)))
                        //.addToBackStack(null)
                        .commit();
                break;
            case R.id.finish_setup_btn:
                // If the fragment is on course mode, add all the selected courses to the db
                if (fragmentMode == MODE_COURSES) {
                    addCoursesInAdapterToDb();
                }

                // Save state about finishing FirstRun and Setup
                preferences.edit().putBoolean(PushUtils.SP_IS_FIRST_RUN, false).apply();

                // TODO: Create course folders in internal storage

                // Start MainActivity
                startActivity(new Intent(getContext(), MainActivity.class));
        }
    }

    // Set adapter to the mainRV with Course Items
    private void setCourseAdapter() {
        // Get the chosen study field id
        int studyFieldID = preferences.getInt(PushUtils.SP_STUDY_FIELD_ID, 1);

        // Make JSONArrayRequest and get the list of courses
        // Base request URL
        String url = PushUtils.URL_GET_COURSES;

        // Append GET parameters
        url = PushUtils.appendGetParameter(PushUtils.API_PARAMS_COURSES_SECTION, sectionCode, url);
        url = PushUtils.appendGetParameter(PushUtils.API_PARAMS_COURSES_STUDY_FIELD, studyFieldID + "", url);

        final ArrayList<Object> courses = new ArrayList<>();

        // Build the request
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // Output the response to the log
                        Log.d(TAG, response.toString());

                        // Parse the JSON Array
                        try {
                            // Check if the array is not empty
                            if (response.length() < 1){
                                Log.d(TAG, "Request returned empty JSON Array");

                                // Show Error Message
                                Snackbar.make(view,
                                        "This section does not contain any courses. Go back and choose a different section",
                                        Snackbar.LENGTH_INDEFINITE).show();

                                return; // Exit if array is empty
                            }

                            // Loop through every JSON object in the array
                            for (int i = 0; i < response.length(); i++){
                                // Get a JSON object from the array
                                JSONObject json = (JSONObject) response.get(i);

                                courses.add(new Course(
                                        json.getInt("id"),
                                        json.getString("name"),
                                        sectionCode,
                                        isSectionPrimary
                                ));

                            }
                            CourseSectionSelectionAdapter adapter = new CourseSectionSelectionAdapter(courses);
                            mainRV.setAdapter(adapter);


                        } catch (JSONException exception){
                            exception.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();

                        String errorMessage = "Unknown Error!";

                        // Set the errorMessage based on the error type
                        if (error instanceof NoConnectionError) {
                            errorMessage = "No Connection";
                        } else if (error instanceof TimeoutError) {
                            errorMessage = "Server took too long to respond";
                        } else if (error instanceof ServerError) {
                            errorMessage = "There was a problem with the server";
                        } else if (error instanceof NetworkError) {
                            errorMessage = "Unknown error with the network";
                        } else if (error instanceof ParseError) {

                        }

                        Log.e(TAG, "VolleyError in setCourseAdapter: " + errorMessage);

                        Snackbar.make(view, errorMessage, Snackbar.LENGTH_INDEFINITE)
                                .setAction("RETRY", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        setAdapter();
                                    }
                                }).show();
                    }
                });

        // Set request retry policy
        request.setRetryPolicy(
                new DefaultRetryPolicy(60000,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Disable Volley Cache
        request.setShouldCache(false);

        // Add to the request queue
        requestQueue.add(request);
    }

    // Set adapter to the mainRV with Sections
    private void setSectionAdapter() {
        // Make JSONArrayRequest and get the list of courses
        // Base request URL
        String url = PushUtils.URL_GET_SECTIONS;

        // Append GET parameters
        url = PushUtils.appendGetParameter(PushUtils.API_PARAMS_SECTIONS_STUDY_FIELD_ID, studyFieldID + "", url);

        // Get the year of the student
        final int year = preferences.getInt(PushUtils.SP_SELECTED_YEAR, 1);

        final ArrayList<Object> sections = new ArrayList<>();

        // Build the request
        JsonArrayRequest request = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // Output the response to the log
                        Log.d(TAG, response.toString());

                        // Parse the JSON Array
                        try {
                            // Check if the array is not empty
                            if (response.length() < 1){
                                Log.d(TAG, "Request returned empty JSON Array");

                                // Show Error Message
                                Snackbar.make(view,
                                        "There are no sections in your study field.",
                                        Snackbar.LENGTH_INDEFINITE).show();

                                return; // Exit if array is empty
                            }

                            // Loop through every JSON object in the array
                            for (int i = 0; i < response.length(); i++){
                                // Get a JSON object from the array
                                JSONObject json = (JSONObject) response.get(i);
                                String sectionCode = json.getString("code");

                                // Don't add sections within the same year
                                if (sectionCode.contains("Y" + year)) continue;

                                sections.add(sectionCode);

                            }
                            CourseSectionSelectionAdapter adapter = new CourseSectionSelectionAdapter(sections);
                            mainRV.setAdapter(adapter);


                        } catch (JSONException exception){
                            exception.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();

                        String errorMessage = "Unknown Error!";

                        // Set the errorMessage based on the error type
                        if (error instanceof NoConnectionError) {
                            errorMessage = "No Connection";
                        } else if (error instanceof TimeoutError) {
                            errorMessage = "Server took too long to respond";
                        } else if (error instanceof ServerError) {
                            errorMessage = "There was a problem with the server";
                        } else if (error instanceof NetworkError) {
                            errorMessage = "Unknown error with the network";
                        } else if (error instanceof ParseError) {

                        }

                        Log.e(TAG, "VolleyError in setSectionAdapter: " + errorMessage);

                        Snackbar.make(view, errorMessage, Snackbar.LENGTH_INDEFINITE)
                                .setAction("RETRY", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        setAdapter();
                                    }
                                }).show();
                    }
                });

        // Set request retry policy
        request.setRetryPolicy(
                new DefaultRetryPolicy(60000,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Disable Volley Cache
        request.setShouldCache(false);

        // Add to the request queue
        requestQueue.add(request);
    }

    private void setAdapter() {
        if (fragmentMode == MODE_COURSES) {
            setCourseAdapter();
        } else if (fragmentMode == MODE_SECTIONS) {
            setSectionAdapter();
        }
    }

    private void addCoursesInAdapterToDb() {
        // Add the selected course to the db
        CourseSectionSelectionAdapter adapter = (CourseSectionSelectionAdapter)mainRV.getAdapter();
        ArrayList<Object> courses = adapter.getList();

        // Added course counter
        int counter = 0;

        // DBHelper
        DBHelper dbHelper = new DBHelper(getContext().getApplicationContext());

        // Loop through every course and add the selected ones to the db
        for (Object object: courses) {
            Course course = (Course)object;
            if (!course.isSelected) continue;

            if (dbHelper.addCourse(course.courseID, course.courseName, course.sectionCode) != -1) {
                counter++;
            }
        }

        // Show a toast about how many courses added to the db
        Toast.makeText(getContext(), counter + " courses added!", Toast.LENGTH_SHORT).show();

        // Close the DBHelper object
        dbHelper.close();
    }

    @Override
    public void onResume(){
        super.onResume();

        // Register BroadcastReceiver
        IntentFilter broadcastFilter = new IntentFilter(PushUtils.CLICKED_ON_SECTION_BROADCAST);
        getActivity().registerReceiver(broadcastReceiver, broadcastFilter);


    }

    @Override
    public void onDetach(){
        super.onDetach();

        // Unregister BroadcastReceiver
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PushUtils.CLICKED_ON_SECTION_BROADCAST)) {
                String section_code = intent.getStringExtra("section_code");
                FragmentManager fragmentManager = getFragmentManager();
                fragmentManager
                        .beginTransaction()
                        .replace(R.id.first_run_activity,
                                CourseSelectionFragment.newInstance(section_code, false))
                        //.addToBackStack(null)
                        .commit();
            }
        }
    };

    // Temporary Class
    class Course {
        int courseID;
        String courseName;
        String sectionCode;
        boolean isSelected;
        boolean isInPrimarySection;

        public Course(int courseID, String courseName, String sectionCode, boolean isInPrimarySection) {
            this.courseID = courseID;
            this.courseName = courseName;
            this.sectionCode = sectionCode;
            this.isInPrimarySection = isInPrimarySection;
        }
    }

}
