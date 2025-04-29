#include <iostream>
#include "esp_log.h"
#include "rtdb.h"
#include "jsoncpp/value.h"
#include "jsoncpp/json.h"
#define RTDB_TAG "RTDB"

namespace ESPFirebase {

// Constructor for the RTDB class. Initializes the FirebaseApp instance and database URL.
RTDB::RTDB(FirebaseApp* app, const char * database_url)
    : app(app), base_database_url(database_url)
{
    // Constructor does not contain additional logic.
}

// Function to retrieve data from the Firebase Realtime Database at a given path.
// It returns a Json::Value object containing the retrieved data.
Json::Value RTDB::getData(const char* path)
{
    // Construct the full URL to access data in the Realtime Database.
    std::string url = RTDB::base_database_url;
    url += path;
    url += ".json?auth=" + this->app->auth_token;

    // Set the appropriate header for the HTTP request.
    this->app->setHeader("content-type", "application/json");

    // Perform the GET request to retrieve data.
    http_ret_t http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_GET, "");

    // Check if the request was successful.
    if (http_ret.err == ESP_OK && http_ret.status_code == 200)
    {
        const char* begin = this->app->local_response_buffer;
        const char* end = begin + strlen(this->app->local_response_buffer);

        Json::Reader reader;
        Json::Value data;

        // Parse the JSON response.
        reader.parse(begin, end, data, false);

        ESP_LOGI(RTDB_TAG, "Data with path=%s acquired", path);
        this->app->clearHTTPBuffer();
        return data;
    }
    else
    {   
        // Log error if GET request fails and attempt to refresh authentication token.
        ESP_LOGE(RTDB_TAG, "Error while getting data at path %s| esp_err_t=%d | status_code=%d", path, (int)http_ret.err, http_ret.status_code);
        ESP_LOGI(RTDB_TAG, "Token expired ? Trying refreshing auth");

        // Refresh authentication token and retry GET request.
        esp_err_t err = this->app->loginUserAccount(this->app->user_account);
        this->app->setHeader("content-type", "application/json");
        http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_GET, "");

        if (http_ret.err == ESP_OK && http_ret.status_code == 200)
        {
            const char* begin = this->app->local_response_buffer;
            const char* end = begin + strlen(this->app->local_response_buffer);

            Json::Reader reader;
            Json::Value data;

            // Parse the JSON response again.
            reader.parse(begin, end, data, false);

            ESP_LOGI(RTDB_TAG, "Data with path=%s acquired", path);
            this->app->clearHTTPBuffer();
            return data;
        }
        else
        {
            // Log error if data could not be retrieved after refreshing the token.
            ESP_LOGE(RTDB_TAG, "Failed to get data after refreshing token. double check account credentials or database rules");
            this->app->clearHTTPBuffer();
            return Json::Value();
        }
    }
}

// Function to update data at the given path in the Firebase Realtime Database using a JSON string.
// Returns ESP_OK if the operation was successful, ESP_FAIL otherwise.
esp_err_t RTDB::putData(const char* path, const char* json_str)
{
    // Construct the full URL to access the Realtime Database and set authentication token.
    std::string url = RTDB::base_database_url;
    url += path;
    url += ".json?auth=" + this->app->auth_token;

    // Set the appropriate header for the HTTP request.
    this->app->setHeader("content-type", "application/json");

    // Perform the PUT request to update data.
    http_ret_t http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_PUT, json_str);
    this->app->clearHTTPBuffer();

    if (http_ret.err == ESP_OK && http_ret.status_code == 200)
    {
        ESP_LOGI(RTDB_TAG, "PUT successful");
        return ESP_OK;
    }
    else
    {
        ESP_LOGE(RTDB_TAG, "PUT failed");
        return ESP_FAIL;
    }
}

// Overloaded function for putting data using a Json::Value object.
esp_err_t RTDB::putData(const char* path, const Json::Value& data)
{
    Json::FastWriter writer;
    std::string json_str = writer.write(data);
    esp_err_t err = RTDB::putData(path, json_str.c_str());
    return err;
}

// Function to post data to the Firebase Realtime Database at a given path using a JSON string.
// Returns ESP_OK if the operation was successful, ESP_FAIL otherwise.
esp_err_t RTDB::postData(const char* path, const char* json_str)
{
    // Construct the full URL to access the Realtime Database and set authentication token.
    std::string url = RTDB::base_database_url;
    url += path;
    url += ".json?auth=" + this->app->auth_token;

    // Set the appropriate header for the HTTP request.
    this->app->setHeader("content-type", "application/json");

    // Perform the POST request to submit data.
    http_ret_t http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_POST, json_str);
    this->app->clearHTTPBuffer();

    if (http_ret.err == ESP_OK && http_ret.status_code == 200)
    {
        ESP_LOGI(RTDB_TAG, "POST successful");
        return ESP_OK;
    }
    else
    {
        ESP_LOGE(RTDB_TAG, "POST failed");
        return ESP_FAIL;
    }
}

// Overloaded function for posting data using a Json::Value object.
esp_err_t RTDB::postData(const char* path, const Json::Value& data)
{
    Json::FastWriter writer;
    std::string json_str = writer.write(data);
    esp_err_t err = RTDB::postData(path, json_str.c_str());
    return err;
}

// Function to update part of the data (PATCH request) at the given path using a JSON string.
// Returns ESP_OK if the operation was successful, ESP_FAIL otherwise.
esp_err_t RTDB::patchData(const char* path, const char* json_str)
{
    // Construct the full URL to access the Realtime Database and set authentication token.
    std::string url = RTDB::base_database_url;
    url += path;
    url += ".json?auth=" + this->app->auth_token;

    // Set the appropriate header for the HTTP request.
    this->app->setHeader("content-type", "application/json");

    // Perform the PATCH request to update part of the data.
    http_ret_t http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_PATCH, json_str);
    this->app->clearHTTPBuffer();

    if (http_ret.err == ESP_OK && http_ret.status_code == 200)
    {
        ESP_LOGI(RTDB_TAG, "PATCH successful");
        return ESP_OK;
    }
    else
    {
        ESP_LOGE(RTDB_TAG, "PATCH failed");
        return ESP_FAIL;
    }
}

// Overloaded function for patching data using a Json::Value object.
esp_err_t RTDB::patchData(const char* path, const Json::Value& data)
{
    Json::FastWriter writer;
    std::string json_str = writer.write(data);
    esp_err_t err = RTDB::patchData(path, json_str.c_str());
    return err;
}

// Function to delete data at the given path in the Firebase Realtime Database.
// Returns ESP_OK if the operation was successful, ESP_FAIL otherwise.
esp_err_t RTDB::deleteData(const char* path)
{
    // Construct the full URL to access the Realtime Database and set authentication token.
    std::string url = RTDB::base_database_url;
    url += path;
    url += ".json?auth=" + this->app->auth_token;

    // Set the appropriate header for the HTTP request.
    this->app->setHeader("content-type", "application/json");

    // Perform the DELETE request to remove data.
    http_ret_t http_ret = this->app->performRequest(url.c_str(), HTTP_METHOD_DELETE, "");
    this->app->clearHTTPBuffer();

    if (http_ret.err == ESP_OK && http_ret.status_code == 200)
    {
        ESP_LOGI(RTDB_TAG, "DELETE successful");
        return ESP_OK;
    }
    else
    {
        ESP_LOGE(RTDB_TAG, "DELETE failed");
        return ESP_FAIL;
    }
}

}
