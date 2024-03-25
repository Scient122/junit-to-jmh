library("jsonlite")
library("changepoint")
library("rlist")
library("kneedle")
library("stringi")

changepoint_detection <- function(input_path, output_path){
args <- commandArgs(trailingOnly = TRUE)
jsonpath <- input_path
result_path <- output_path
string_end_index <-stri_locate_last(jsonpath,regex=c('Filtered'))[[1]]
string_start_index <- stri_locate_last(jsonpath,regex=c('\\\\'))[[1]]

name <- substring(jsonpath,string_start_index+1,string_end_index-1)

data <- fromJSON(jsonpath)
data_frame <- as.data.frame(data)
averages_data <- as.vector(data_frame$average)
penalty_boundaries <- c(4 , 100000)
result <- cpt.meanvar(averages_data,penalty="CROPS",pen.value=penalty_boundaries,method = "PELT",class=FALSE)
result <- list.reverse(result[[1]])
changepoints_numbers <- c()
penalty_values <- c()
changepoint_number_index <- 2
penalty_values_index <- 3

for (i in 1:(length(result)/3)){
    changepoints_numbers <- c(changepoints_numbers,result[[changepoint_number_index]])
    penalty_values <- c(penalty_values, result[[penalty_values_index]])
    changepoint_number_index <- changepoint_number_index + 3
    penalty_values_index <- penalty_values_index + 3 
    
}

knee <- kneedle(changepoints_numbers,penalty_values)
pelt_penalty_value <- knee[[2]]
pelt_result <- cpt.meanvar(averages_data,penalty="Manual",pen.value=pelt_penalty_value,method = "PELT", class = FALSE)
#VERIFICARE SE L'ULTIMO CHANGEPOINT CORRISPONDE SEMPRE ALL'ULTIMO DATAPOINT.
last_point <- pelt_result[[length(pelt_result)]]
last_changepoint <- pelt_result[[length(pelt_result)-1]]
last_segment_points_sum = 0
last_segment_points_number = last_point - last_changepoint + 1
for(i in last_changepoint : last_point){
    
    last_segment_points_sum <- last_segment_points_sum + averages_data[[i]]
}

last_segment_mean <- last_segment_points_sum / last_segment_points_number
percentage <- (last_segment_mean * 5) / 100

sequence_start <- last_changepoint
sequence_points_number <- last_segment_points_number

current_segment_end <- last_changepoint-1

for(i in 1:(length(pelt_result)-1)){

    if(i == (length(pelt_result)-1)){
        current_segment_start <- 1
    }
    else{
        current_segment_start <- pelt_result[[length(pelt_result)-1-i]]
    }
    current_segment_points_number <- current_segment_end - current_segment_start + 1
    current_segment_points_sum <- 0
    for(j in current_segment_start : current_segment_end){
        current_segment_points_sum <- current_segment_points_sum + averages_data[[j]]
    }
    current_segment_mean <- current_segment_points_sum / current_segment_points_number
    if(current_segment_mean >= (last_segment_mean - percentage) & current_segment_mean <= (last_segment_mean + percentage)){
        sequence_start <- current_segment_start
        sequence_points_number <- sequence_points_number + current_segment_points_number
    }
    else{
        break
    }
    
    current_segment_end <- current_segment_start - 1
}

warm_up_duration <- 0
if(sequence_points_number >= 500){
    for(i in 1:(sequence_start - 1)){
        warm_up_duration <- warm_up_duration + averages_data[[i]]
    }
} else{
    warm_up_duration <- Inf
}

write(paste(name,warm_up_duration),file = paste(result_path, "Results.txt"), append = TRUE)
}

args <- commandArgs(trailingOnly = TRUE)
input_folder_path <- args[1]
result_path <- args[2]

files <- list.files(input_folder_path, full.names = TRUE)

for(file in files) {
    changepoint_detection(file,result_path)
}

